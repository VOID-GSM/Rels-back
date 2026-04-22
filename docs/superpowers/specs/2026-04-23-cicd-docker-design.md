# CI/CD & Docker 설계 문서

**날짜:** 2026-04-23
**프로젝트:** Rels-back (Spring Boot 4.0.5 + Java 21 + MySQL)

---

## 1. 개요

학교 서버에 Docker 기반으로 애플리케이션을 배포하기 위한 CI/CD 파이프라인과 컨테이너 환경을 구축한다.

---

## 2. 아키텍처

```
[GitHub Repository]
    │
    ├── develop 브랜치 push → CI (테스트만)
    │
    └── main 브랜치 push → CI + CD (테스트 → SSH 배포)
                               │
                        [학교 서버]
                        git pull
                        docker compose up --build -d
                               │
                    ┌──────────┴──────────┐
                  [app:8080]          [mysql:3306]
                  Spring Boot          MySQL 8.0
                  (외부 노출)          (내부 전용)
```

---

## 3. Dockerfile (Multi-stage)

- **Stage 1 - builder:** `gradle:9.4.1-jdk21` 이미지에서 `./gradlew build -x test` 실행
- **Stage 2 - runtime:** `eclipse-temurin:21-jre-alpine` 경량 이미지에 JAR 복사 후 실행
- 최종 이미지는 JRE만 포함, 빌드 도구 제외로 용량 최소화

---

## 4. docker-compose.yml (프로덕션)

| 서비스 | 이미지 | 포트 | 볼륨 |
|--------|--------|------|------|
| `app` | 로컬 빌드 | 8080:8080 (외부 노출) | - |
| `mysql` | mysql:8.0 | 3306 (내부 전용) | mysql_data (named volume) |

- `app`은 `mysql` healthcheck 통과 후 시작 (`depends_on: condition: service_healthy`)
- 두 서비스는 동일 Docker 네트워크 공유
- 환경변수는 `.env` 파일에서 주입 (git 미포함)

---

## 5. docker-compose.staging.yml (스테이징 오버라이드)

- 프로덕션 compose를 base로 하고 스테이징 전용 환경변수 오버라이드
- `docker compose -f docker-compose.yml -f docker-compose.staging.yml up` 으로 실행

---

## 6. GitHub Actions

### ci.yml — develop 브랜치 push 시
1. 코드 체크아웃
2. Java 21 설정
3. Gradle 테스트 실행 (`./gradlew test`)
4. 결과 리포트

### cd.yml — main 브랜치 push 시
1. 코드 체크아웃
2. Java 21 설정
3. Gradle 테스트 실행
4. SSH로 학교 서버 접속
5. `git pull origin main`
6. `docker compose up --build -d`

---

## 7. 환경변수

### .env.example (템플릿, git 포함)
```
# DB
MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=rels
MYSQL_USER=
MYSQL_PASSWORD=

# Spring
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/rels
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

# OAuth
DATAGSM_CLIENT_ID=
DATAGSM_CLIENT_SECRET=
DATAGSM_REDIRECT_URIS=
```

### GitHub Secrets (CD에서 사용)
| Secret 이름 | 설명 |
|------------|------|
| `SERVER_HOST` | 학교 서버 IP |
| `SERVER_USER` | SSH 접속 유저명 |
| `SERVER_SSH_KEY` | SSH Private Key |
| `SERVER_DEPLOY_PATH` | 서버 내 프로젝트 경로 |

---

## 8. 파일 구성

```
Rels-back/
├── Dockerfile
├── docker-compose.yml
├── docker-compose.staging.yml
├── .env.example
└── .github/
    └── workflows/
        ├── ci.yml
        └── cd.yml
```

---

## 9. 결정 사항

- **배포 방식:** SSH + git pull + docker compose up --build (Registry 없음)
- **포트:** 8080 외부 노출, 3306 내부 전용
- **DB:** MySQL 컨테이너 포함 (docker-compose 관리)
- **스테이징/프로덕션:** develop(CI만) / main(CI+CD) 2단계

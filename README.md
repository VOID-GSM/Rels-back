# Rels (Relay Study)

Rels는 **광주소프트웨어마이스터고등학교 릴레이 스터디**의 전체 운영 과정을 하나의 플랫폼에서 효율적으로 관리하기 위한 서비스입니다.

이 저장소는 **백엔드**입니다. 프론트엔드는 [Rels-front](https://github.com/VOID-GSM/Rels-front)에서 관리합니다.

## 구성

이 저장소는 Spring Boot 기반의 백엔드 애플리케이션입니다.

| 패키지/디렉터리 | 설명 |
|---|---|
| `domain/auth` | DataGSM OAuth2 인증, 로그인 처리 및 토큰/세션 검증 |
| `domain/lecture` | 강연 CRUD, 수강 신청/대기, 출석 관리 및 학생회 승인 Workflow |
| `domain/notice` | 전교생 대상 공지사항 관리 (학생회 전용) |
| `domain/user` | 사용자 엔티티 및 권한(USER / ADMIN) 관리 |
| `global/aop` | AOP 기반 공통 로깅 |
| `global/config` | Web, JPA, Async, Scheduler 등 앱 전역 Configuration |
| `global/controller` | 공통 헬스체크 및 글로벌 컨트롤러 |
| `global/security` | Spring Security 설정, OAuth2 핸들러 및 인증 가드/필터 |

## 기술 스택

- **Framework & Core:** Java 21, Spring Boot 3.x
- **Database & ORM:** MariaDB, Spring Data JPA
- **Security & Auth:** Spring Security, OAuth2 (DataGSM 연동)
- **Concurrency Control:** Pessimistic Lock (수강 신청 동시성 제어)
- **Automation & Integration:** Spring Scheduler, Discord Bot (JDA)
- **Build Tool:** Gradle

## 주요 기능

### 👨‍🎓 학생 (USER)
- **DataGSM OAuth2 인증**: 미인증 사용자 접근 제어 및 사용자 역할(USER / ADMIN) 세분화
- **강연 관리**: 등록, 수정, 삭제 (작성자 본인 권한 분기) 및 학년별/전체 정원(10~30명) 설정
- **강연 신청·취소 & 대기열**:
  - 게시 당일 16:20 수강 신청 오픈 제어 (`403 FORBIDDEN` 반환)
  - 정원 초과 시 대기 상태(`WAITING`) 자동 등록 및 취소 시 대기 1순위 자동 승격
  - 비관적 락(Pessimistic Lock) 기반 동시 신청 데이터 일관성 보장

### 🏛️ 학생회 (ADMIN)
- **강연 정보 관리**: 학생회 권한으로 개설된 강연 전체 정보 수동 수정
- **강연 승인 Workflow**: 강연 승인/거절 처리 및 거절 사유 전달 (미승인 강연 노출 차단)
- **출석 관리**: 수강생 대상 출석·결석·지각·미확인 상태 일괄 배치 업데이트
- **공지사항 관리**: ADMIN 전용 공지사항 등록·수정·삭제 API

### ⚙️ 강연 라이프사이클 (Scheduler)
- `개설 미정(OPEN)` ➔ `개설 확정(CONFIRMED)` / `개설 불확정(UNCONFIRMED)` ➔ `강연 종료(CLOSE)` 4단계 자동 상태 동기화

## 버그 제보 및 이슈

버그를 발견하거나 기능 제안이 있다면 GitHub 이슈 트래커를 이용해 주세요.

- **이슈 트래커:** [https://github.com/VOID-GSM/Rels-back/issues/](https://github.com/VOID-GSM/Rels-back/issues/new)

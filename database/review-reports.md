# 리뷰 신고 배포 및 API

현재 Flyway는 비활성화되어 있으므로 `review-reports.sql` → `review-report-types.sql` 순서로 대상 PostgreSQL DB에 적용한 후 애플리케이션을 배포합니다.
`ddl-auto: validate`는 테이블을 생성하지 않습니다. SQL은 기존 리뷰·회원 데이터를 변경하지 않습니다.
재실행 시 기존 테이블 정의를 교정하지 않으므로 적용 후 컬럼·외래 키·고유 제약·인덱스를 확인합니다.
SQL 파일은 UTF-8이며, 한글 보정 항목이 있으므로 실행 도구에서도 UTF-8로 읽어야 합니다.

- 등록: `POST /api/v1/reviews/{reviewId}/reports`, 로그인 필수, 본문 `{"reportTypeId":1,"reason":"상세 내용"}`, 성공 201(본문 없음).
- 선택 사유 ID와 상세 내용은 모두 필수입니다. 상세 내용은 앞뒤 공백 제거 후 1~1,000자입니다.
  없는 사유는 404, 비활성 사유는 400, 본인 리뷰 및 탈퇴 예약 회원의 신고는 403입니다.
- 동일 회원·리뷰는 한국 날짜 기준 하루 1회이며 중복은 409. 신고 시각은 UTC, 제한 날짜는 Asia/Seoul 기준입니다.
  날짜별 고유 제약으로 제한하므로 자정에 제한을 초기화하는 스케줄러는 필요하지 않습니다.
- 관리자 조회: `GET /api/v1/admin/reviews/reports?page=1&size=50&reviewId=1`, ADMIN만 허용.
  `reviewId`는 선택이며 최신 신고 시각·ID 내림차순입니다. 응답은 `content`, `page`, `size`, `totalElements`, `totalPages`입니다.
  각 항목은 `id`, `reviewId`, `reviewTitle`, `author`, `reporter`, `reportType: {id, name}`, `reason`, `createdAt`을 반환합니다.
- 일반 회원의 신고 조회·수정·삭제 API는 없습니다. 자동 숨김·삭제와 신고 처리 상태는 제공하지 않습니다.
- 리뷰 삭제 시 신고도 같은 트랜잭션에서 삭제됩니다. 신고자 회원 삭제 시 `reporter`만 null이 되며 사유·시각은 남습니다.

## 리뷰 상세의 본인 신고 여부

`GET /api/v1/reviews/{id}`는 다음 boolean 필드를 추가로 반환하며 `Cache-Control: no-store`를 적용합니다.

- `isReported`: 로그인 회원이 해당 리뷰를 한 번이라도 신고했으면 true입니다.
- `isReportedToday`: 로그인 회원이 한국 날짜 기준 오늘 해당 리뷰를 신고했으면 true입니다.
- 비로그인·신고 이력 없음은 둘 다 false입니다. 어제 신고하고 오늘은 신고하지 않았다면 true/false입니다.
- 사유가 비활성화되어도 기존 신고는 이력에 포함됩니다. 다른 회원이나 다른 리뷰의 신고는 포함하지 않습니다.
- 신고 내용이나 다른 신고자의 정보는 노출하지 않습니다. 목록·수정용 응답에는 이 필드를 추가하지 않습니다.

## 신고 사유 선택 항목

- 로그인 회원: `GET /api/v1/review-report-types` → 활성 항목만 `[{id, name, displayOrder}]`로 조회합니다.
- 관리자: `GET /api/v1/admin/review-report-types` → 비활성 항목까지 `[{id, name, displayOrder, active}]`로 조회합니다.
- 관리자 생성: `POST /api/v1/admin/review-report-types`, 본문 `{"name":"광고·도배","displayOrder":0}` → 201, 활성 상태로 생성합니다.
- 관리자 수정: `PUT /api/v1/admin/review-report-types/{id}`, 본문 `{"name":"광고·도배","displayOrder":1,"active":false}` → 200.
  이름과 `active`는 필수입니다. `displayOrder`를 생략하면 저장된 순서를 유지합니다. `active`로 비활성화·재활성화하며 실제 삭제 API는 없습니다.
- 관리자 순서 저장: `PUT /api/v1/admin/review-report-types/order`, 본문 `{"reportTypeIds":[2,1]}` → 200과 저장된 전체 목록. 비활성 항목까지 포함해 전체 ID를 한 번씩 보내며, 중복은 400, 항목 집합이 달라졌으면 409입니다.
- 이름은 앞뒤 공백 제거 후 1~100자이며 중복은 409입니다. 표시 순서는 0 이상이고 같은 값도 허용합니다.
- 두 목록 모두 `displayOrder ASC, id ASC`로 정렬하고 `Cache-Control: no-store`를 반환합니다.
  요청마다 DB를 조회하므로 항목 변경에 서버 재배포나 캐시 초기화가 필요하지 않습니다.
- 사유 이름 변경은 기존 신고 조회에도 반영됩니다. 비활성화해도 기존 신고 연결과 관리자 조회는 유지됩니다.
- 등록은 사유 행의 읽기 잠금을 커밋까지 유지합니다. 관리자가 먼저 비활성화하면 등록을 거절하고,
  등록이 먼저 잠금을 획득하면 해당 등록이 완료된 후 비활성화됩니다.

## 기존 데이터 및 연동 순서

1. 신고 등록 트래픽을 중단한 상태에서 SQL을 적용합니다. 기존 신고 테이블이 있으면 첫 SQL은 재적용할 수 있습니다.
2. 두 번째 SQL은 기존 신고의 선택 사유를 비활성 항목 `기존 신고(사유 미분류)`로 보정한 후 외래 키와 NOT NULL을 설정합니다.
   보정할 신고가 없으면 이 항목도 생성하지 않습니다. 기존 상세 내용·신고자·시각은 변경하지 않습니다.
3. 백엔드를 배포하고 관리자 API로 실제 선택 항목을 생성합니다. 엔티티의 예시 주석은 자동 초기 데이터가 아닙니다.
4. 프론트에서 선택 목록 GET과 `reportTypeId` 전송을 연동한 뒤 신고 등록을 개방합니다.

`reportTypeId`가 없는 이전 프론트 요청은 400으로 거절됩니다.
두 번째 SQL 적용 후에는 새 컬럼을 채우지 않는 구버전 백엔드의 신고 등록도 실패하므로 배포 중 신고 등록을 계속 열어두지 않습니다.

기존 버전으로 롤백하면 새 테이블을 정리하는 삭제 코드가 없으므로, 신고 데이터가 있는 상태에서는 리뷰·회원 삭제가 외래 키로 차단될 수 있습니다.
롤백이 필요하면 삭제 작업을 중단하고 신고 데이터 보존 및 정리 방식을 먼저 결정해야 합니다.

## 검증

`ReviewReportTests`는 기본적으로 H2를 사용합니다. `REVIEW_REPORT_TEST_URL`에 PostgreSQL JDBC URL을,
`REVIEW_REPORT_TEST_USER`, `REVIEW_REPORT_TEST_PASSWORD`에 접속 정보를 지정하면 같은 테스트를 PostgreSQL에서 실행할 수 있습니다.
이 테스트는 `create-drop`을 사용하고 SQL 재적용 테스트에서 신고 테이블도 재생성하므로 반드시 폐기 가능한 전용 DB를 사용합니다.
PostgreSQL에서는 기존 데이터 보정·SQL 재적용과 신고 등록/사유 비활성화 간의 실제 잠금 대기도 검증합니다.

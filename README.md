# NP 뷰어

노벨피아 공식 앱의 불편함을 해소하기 위해 제작된 비공식 Android WebView 앱입니다.

## 만든 이유

노벨피아 공식 앱을 사용하면서 다음과 같은 불편함이 있었습니다.

* 느린 앱 로딩: 앱을 실행하면 콘텐츠가 표시되기까지 체감상 시간이 너무 오래 걸렸습니다.
* 로딩 진행 상황을 알 수 없음: 페이지가 로딩 중인지, 멈춘 것인지 시각적으로 확인할 방법이 없었습니다.
* 네트워크 오류 시 앱 재시작 필요: 네트워크 연결이 불안정한 환경에서 한 번 오류가 발생하면, 이후 인터넷이 복구되어도 모든 페이지가 접근 불가 상태로 남아 앱을 완전히 종료했다가 다시 시작해야 했습니다.

## 주요 기능

* 상단 로딩 진행 표시줄: 페이지 로딩 진행 상황을 화면 상단에서 실시간으로 확인할 수 있습니다.
* 당겨서 새로고침: 화면을 아래로 당기면 페이지를 새로고침합니다.
* 네트워크 오류 화면 및 재시도: 페이지 로딩에 실패하면 오류 화면이 표시되고, 버튼을 누르는 것만으로 페이지를 새로고침할 수 있습니다.
* 뒤로가기 두 번 눌러 앱 종료: 노벨피아 공식 앱과 마찬가지로, 가장 첫 화면에서 뒤로가기 키를 두 번 눌러 앱을 종료할 수 있습니다.
* 뷰어 볼륨 버튼 페이지 이동: 웹소설 뷰어 페이지에서 볼륨 업/다운 버튼을 눌러 이전/다음 페이지로 이동할 수 있습니다.

## 광고 차단 엔진

앱은 `@adguard/tsurlfilter` 기반 JavaScript 런타임 대신 Kotlin 기반 필터 엔진을 사용합니다. 필터 목록은 갱신 시점에 한 번만 파싱되어 읽기 전용 객체로 컴파일되며, WebView 요청 처리 시에는 해당 객체만 조회합니다.

### 구현 목표

* `shouldInterceptRequest()` 경로에서 JavaScript Sandbox 호출을 완전히 제거합니다.
* Network 규칙과 Cosmetic 규칙을 별도 객체로 분리해, 요청 판정과 페이지별 cosmetic payload 계산을 독립적으로 수행합니다.
* 필터 목록 해석에 실패한 줄은 앱을 중단시키지 않고 조용히 skip 합니다.
* 컴파일된 규칙 객체는 immutable하게 유지하고, 요청 판정 결과는 내부 캐시로 재사용합니다.

### 지원하는 Network 규칙 문법

다음 문법만 지원합니다.

* 일반 부분 문자열 규칙: `ads`, `banner/ads/`
* URL 시작 anchor: `|https://example.com/ads`
* 도메인 anchor: `||example.com^`, `||cdn.example.com/banner`
* URL 끝 anchor: `ads.js|`
* wildcard: `*`
* separator placeholder: `^`
* allowlist: `@@||example.com^`
* 리소스 타입 옵션: `$document`, `$subdocument`, `$script`, `$stylesheet`, `$object`, `$image`, `$xmlhttprequest`, `$media`, `$font`, `$other`
* 리소스 타입 제외 옵션: `$~image`, `$~script` 등
* 문서 도메인 제한: `$domain=example.com|foo.com|~bar.com`

### 지원하지 않는 Network 규칙 문법

아래 문법은 지원하지 않으며, 해당 규칙 줄은 skip 됩니다.

* 정규식 규칙: `/.../`
* `third-party`, `match-case`, `important`, `badfilter`, `redirect`, `removeparam`, `csp`, `cookie` 등 고급 옵션
* `denyallow`, `sitekey`, `header`, `method`, `permissions`, `urltransform` 등 부가 옵션
* 스크립틀릿/주입 규칙 및 uBO 고급 확장 문법
* `$` 옵션에 지원하지 않는 토큰이 섞여 있는 규칙 전체

### 지원하는 Cosmetic 규칙 문법

다음 문법을 지원합니다.

* 전역 cosmetic 규칙: `##.ad-banner`
* 도메인 한정 cosmetic 규칙: `example.com##.ad-banner`
* 다중 도메인 한정: `example.com,foo.com##.ad-banner`
* 제외 도메인: `~m.example.com##.ad-banner`
* cosmetic 예외 규칙: `example.com#@#.ad-banner`, `#@#.ad-banner`

### 지원하지 않는 Cosmetic 규칙 문법

아래 문법은 지원하지 않으며, 해당 규칙 줄은 skip 됩니다.

* `#$#`, `#%#`, `#?#`, `#@$#` 등 scriptlet/extended cosmetic 문법
* CSS 주입 규칙, procedural cosmetic 규칙, pseudo-class 확장
* selector 부분이 비어 있거나 `##`가 중첩된 잘못된 규칙

### 동작 방식 및 제약

* Network 규칙은 호스트 anchor 버킷, 토큰 인덱스, fallback 목록으로 분리해 요청마다 후보 규칙만 검사합니다.
* WebView는 여러 요청을 병렬로 보낼 수 있으므로, 요청 경로에서는 공유 mutable 파서를 사용하지 않고 읽기 전용 엔진만 조회합니다.
* Cosmetic payload는 URL 단위로 캐시되며, 페이지 진입 시 한 번 계산한 뒤 문서 시작 스크립트가 이를 주입합니다.
* `domain=` 옵션은 문서 URL 기준으로 매칭합니다. main frame 요청처럼 referrer가 없는 경우에는 요청 URL의 host를 사용합니다.
* 고급 문법 호환성보다 앱 안정성과 응답 속도를 우선합니다.

## 주의사항

이 앱은 노벨피아의 공식 앱이 아니며, 노벨피아와 아무런 연관이 없는 비공식 Android WebView 래퍼입니다.

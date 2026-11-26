# 승패·재대결 작업 상태

2026-11-26: #18의 GameState/PacmanServer 연결 및 #20의 3초 결과 대기·역할 교대·재준비를 구현했다.

- 상세 동작: [서버 게임 진행 안내](server-gameplay.md)
- 프로토콜: [통신 규약](protocol.md)
- 핵심 경계: RoundResultTests, RoundLifecycleTests
- 실제 TCP 및 반복 재대결: RematchTests
- 기존 이동/파워 테스트의 작은 맵은 승패로 일찍 끝나지 않도록 별도 일반 먹이를 추가했다.
- 실행: `./server.ps1 -Task test`, `./server.ps1 -Task test-ui`, `./server.ps1 -Task build`
- GitHub Actions는 Java 21에서 위 검사와 원본 전체 소스 컴파일을 수행한다.
- 로컬 실행은 Windows sandbox 초기화 오류로 불가능했다. 실제 통과 여부는 PR의 최신 커밋 체크 결과로 확인한다.
- 결과 안내 UI(#19), 재대결 안내 UI(#21), 실제 PC 두 대 검증(#24)은 이 서버 작업과 별도다.

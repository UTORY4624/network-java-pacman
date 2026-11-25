# #18 승패 판정 작업 인계 (2026-11-25)

## 현재 범위
- 기준 main: 54a2fdaf7817cb91f09eb974e8b0456ffc4010ef (#16 머지).
- RoundResult: 목숨 소진 > 일반 먹이 완료 > 시간 초과 순서로 판정한다.
- 파워 아이템은 수집 완료 조건에서 제외한다.
- ROUND_RESULT 메시지 생성은 protocol.md의 winner/reason/roundId/phase를 따른다.
- RoundResultTests를 server.ps1 -Task test에 추가했다.
- **게임 루프에 아직 연결하지 않았다. 실제 플레이에서 결과 메시지가 나오는 구현은 미완료다.**
- 컴파일/Java 테스트 미실행: Windows sandbox 초기화가 실패하여 명령이 시작되지 않았다.
- 이 문서/PR의 테스트 목록은 작성한 검사이며 통과 보고가 아니다.

## 다음 작업
1. 먼저 .\server.ps1 -Task test로 이번 추가 코드와 기존 검사 컴파일/실행.
2. GameState에 확정된 RoundResult를 저장하고 각 1ms 갱신의 이동 -> 먹이/아이템 -> 접촉 이후에 판정한다. 결과가 한 번 정해지면 변경하지 않는다.
3. 종료 시 phase=RESULT, 양측 requestedDirection=null 및 이동 적립/대기 정리를 적용한다. 그 뒤 advance와 MOVE가 상태를 바꾸지 않는지 검사한다.
4. PacmanServer.tick에서 마지막 GAME_STATE와 동일 ROUND_RESULT를 두 사용자에게 한 번만 전송한다. 반복 tick에서 결과가 다시 전송되지 않도록 라운드별 가드가 필요하다.
5. 결과 직후 gameRunning을 단순히 false로 바꾸면 ROLE/READY가 허용될 수 있다. #20 역할 교대 구현 전에는 RESULT에서 ROLE/READY도 거절하도록 유지한다. 3초 뒤 교대/재대결은 #20 범위다.
6. 상대 종료와 송신 실패로 gameState가 비워지는 경로를 확인한다. broadcast 중 재진입 및 라운드 중단에 주의한다.
7. 실제 TCP 소켓 두 개로 동일 결과, 1회 전송, 종료 후 상태 불변과 입력 거절을 검증한다.
8. docs/protocol.md, docs/server-gameplay.md의 구현 범위 설명을 실제 연결 완료 후 갱신한다.

## 기존 테스트의 선행 조정
GameplayTests의 작은 테스트 맵 일부는 일반 먹이가 0개이거나 이동 도중 유일한 먹이를 먹는다. 승패를 연결하면 이동/파워 테스트가 의도보다 일찍 끝난다.
- 승리 자체를 검사하지 않는 맵에는 접근하지 않는 일반 먹이를 남겨 기존 테스트 목적을 유지한다.
- 팩맨 이동 경로의 먹이를 없애서 해결하지 않는다. 먹이 수집 검사 목적을 보존해야 한다.
- DetectionTests, GameUiScenario와 ServerTests의 직접 상태 변경도 함께 확인한다.

## 필수 실제 갱신 테스트
- 마지막 먹이 단독: PACMAN / ALL_PELLETS.
- 마지막 목숨 소진: GHOST / NO_LIVES.
- 먹이가 남고 시간 0: GHOST / TIMEOUT.
- 마지막 먹이 + 치명적 접촉: GHOST / NO_LIVES.
- 마지막 먹이 + 시간 0 (목숨 남음): PACMAN / ALL_PELLETS.
- 세 조건 동시: GHOST / NO_LIVES.
- 파워 획득 + 접촉: 파워 적용 후 접촉을 판정.
- 마지막 먹이 후 같은 advance 호출의 남은 시간은 더 진행하지 않음.
- 결과 이후 tick/MOVE/READY/ROLE, 연결 종료 경로 확인.

## 재개
미완료 로컬 변경이 없는 작업 폴더에서:
```powershell
git fetch origin
git switch codex/server-round-result
.\server.ps1 -Task test
```
Draft 유지. #18 종료나 main 머지는 구현 연결과 검증이 끝난 뒤 결정한다.

# 팩맨 vs 유령 TCP 통신 규약

관련 이슈: [#6 메시지 종류·데이터 형식·TCP 메시지 경계 정의](https://github.com/UTORY4624/network-java-pacman/issues/6)

이 문서는 서버와 두 클라이언트가 같은 방식으로 통신하기 위한 기준이다. 게임 규칙은 [게임 규칙 문서](game-rules.md)를 따른다. 현재는 통신 형식을 먼저 정한 문서이며, 각 메시지의 실제 처리는 이후 서버·클라이언트 이슈에서 구현한다.

## 1. 기본 규칙

- TCP와 UTF-8을 사용한다.
- 클라이언트 요청은 대문자 명령 한 줄로 보낸다. 예: `ROLE PACMAN`
- 서버 응답은 JSON 객체 한 줄로 보낸다. 모든 응답에는 `type`이 있다.
- 메시지 하나의 끝은 줄바꿈(`\n`)이다. Windows의 `\r\n`도 줄바꿈 하나로 처리한다.
- 보내는 쪽은 메시지 뒤에 줄바꿈을 붙이고 바로 `flush()`한다. 받는 쪽은 `readLine()`으로 한 줄씩 읽는다.
- TCP는 한 번 보낸 내용이 나뉘거나 여러 메시지가 붙어서 도착할 수 있다. 한 번의 `read()` 결과를 메시지 하나라고 가정하지 않는다.
- 클라이언트 명령은 공백을 제외하고 256자를 넘지 않는다. 빈 줄·알 수 없는 명령·잘못된 인자는 `ERROR`로 안내한다.

서버가 보낸 JSON 안에는 줄바꿈 문자를 넣지 않는다. JSON의 문자열에는 필요한 경우 이스케이프 처리를 사용한다.

## 2. 값 목록

| 구분 | 허용 값 | 설명 |
| --- | --- | --- |
| 역할 `role` | `PACMAN`, `GHOST`, `null` | 아직 역할을 고르지 않았으면 `null` |
| 방향 `direction` | `UP`, `DOWN`, `LEFT`, `RIGHT` | 마지막으로 받은 이동 방향 |
| 라운드 상태 `phase` | `LOBBY`, `PLAYING`, `RESULT` | 대기·진행·결과 화면 상태 |
| 캐릭터 상태 `status` | `ACTIVE`, `RESPAWNING`, `RETURNING` | 일반 이동·팩맨 재배치 대기·유령 복귀 대기 |
| 승자 `winner` | `PACMAN`, `GHOST` | 라운드가 끝났을 때만 사용 |
| 종료 이유 `reason` | `ALL_PELLETS`, `NO_LIVES`, `TIMEOUT` | 팩맨 먹이 완료·팩맨 목숨 소진·시간 초과 |

좌표 `x`, `y`는 맵의 칸 번호를 사용하며 왼쪽 위를 `(0, 0)`으로 한다. 서버가 좌표 계산과 벽 충돌을 처리하며, 클라이언트는 받은 좌표를 표시만 한다.

## 3. 클라이언트 요청

클라이언트는 아래 명령만 서버에 보낸다. 명령을 보내도 상태가 허용하지 않으면 서버는 연결을 끊지 않고 `ERROR`를 보낸다.

| 명령 | 보낼 수 있는 때 | 설명 |
| --- | --- | --- |
| `ROLE PACMAN` | `LOBBY` | 팩맨 역할 선택 |
| `ROLE GHOST` | `LOBBY` | 유령 역할 선택 |
| `READY` | 본인 역할을 고른 뒤 `LOBBY` | 본인의 준비 상태를 요청. 상대가 아직 없거나 준비하지 않았으면 대기한다. 준비 취소가 필요하면 후속 이슈에서 별도 명령을 추가한다. |
| `MOVE UP` 등 | `PLAYING`이고 본인이 대기 상태가 아닐 때 | 서버에 다음 이동 방향 전달 |
| `PING` | 연결 중 언제나 | 연결 유지 확인 |
| `QUIT` | 연결 중 언제나 | 정상 종료 요청 |

예시는 아래와 같다.

```text
ROLE PACMAN
READY
MOVE LEFT
PING
```

`ROLE`은 같은 역할을 이미 다른 사용자가 선택했으면 실패한다. 역할을 다시 고르면 해당 사용자의 준비 상태는 `false`로 돌아간다. `READY`는 역할 선택 전 또는 이미 진행 중일 때 사용할 수 없다. 한 명만 준비한 경우에는 `LOBBY`를 갱신하고 상대를 기다린다. `MOVE`는 대기·결과 화면, 팩맨 재배치 대기, 유령 복귀 대기 중에는 적용하지 않으며 입력을 저장해 두지 않는다.

## 4. 서버 응답

### 접속과 대기 상태

연결이 허용되면 서버는 먼저 `WELCOME`을 보낸다. `playerId`는 서버가 연결별로 정한 번호이며, 다시 접속하면 새 번호를 받는다.

```json
{"type":"WELCOME","protocol":1,"playerId":1,"heartbeatMs":10000}
```

그 뒤 접속·역할·준비 상태가 바뀔 때마다 두 사용자에게 `LOBBY`를 보낸다. `ready`는 각 사용자의 준비 상태다.

```json
{"type":"LOBBY","phase":"LOBBY","players":[{"id":1,"role":"PACMAN","ready":true},{"id":2,"role":"GHOST","ready":false}]}
```

서로 다른 역할의 두 사용자가 모두 준비하면 서버는 한 번만 `GAME_START`를 보낸다. 이후 바로 첫 `GAME_STATE`를 보낸다.

```json
{"type":"GAME_START","roundId":1,"phase":"PLAYING","remainingMs":180000}
```

### 게임 상태

서버는 첫 구현 기준으로 게임 중 100ms마다, 그리고 시작·재배치·파워 획득·접촉처럼 중요한 상태가 바뀐 직후 `GAME_STATE`를 보낸다. `sequence`는 같은 `roundId` 안에서 1씩 증가한다. 클라이언트는 이전 번호보다 작은 상태를 받으면 표시하지 않는다. 전송 주기는 두 PC 플레이 테스트 후 조정할 수 있다.

```json
{"type":"GAME_STATE","roundId":1,"sequence":17,"phase":"PLAYING","remainingMs":178300,"self":{"id":1,"role":"PACMAN","x":10,"y":8,"direction":"LEFT","status":"ACTIVE","lives":2,"powerRemainingMs":0},"opponent":null,"food":{"pellets":[[1,1],[2,1]],"powerItems":[[13,3]]}}
```

- `self`에는 받는 사용자의 위치와 상태를 항상 넣는다.
- `opponent`는 상대가 탐지 거리 5칸 이하일 때만 넣는다. 범위 밖이면 `null`로 보내며, 상대 좌표를 이전 값으로 남기지 않는다.
- `food`에는 남아 있는 일반 먹이와 파워 아이템 좌표를 항상 넣는다. 상대 위치와 달리 먹이 변화는 두 사용자에게 모두 공유한다.
- `lives`와 `powerRemainingMs`는 팩맨 상태에서 사용한다. 유령에는 넣지 않는다.
- `status`가 `RESPAWNING`이면 팩맨 재배치 3초, `RETURNING`이면 유령 복귀 3초를 뜻한다. 자세한 시간 처리 규칙은 게임 규칙 문서를 따른다.

### 결과와 재대결

라운드가 끝나면 서버는 두 사용자에게 `ROUND_RESULT`를 보낸다.

```json
{"type":"ROUND_RESULT","roundId":1,"phase":"RESULT","winner":"GHOST","reason":"NO_LIVES"}
```

결과를 보여 준 뒤 서버는 연결은 유지하고 역할을 자동으로 교대한다. 위치·먹이·목숨·시간·파워 상태·이전 이동 입력·준비 상태를 초기화한 뒤 `LOBBY`를 보낸다. 두 사용자는 다시 `READY`를 보내 다음 라운드를 시작한다.

### 연결 종료와 접속 유지

클라이언트가 `PING`을 보내면 서버는 아래처럼 응답한다. 기본 연결 유지 주기는 `WELCOME.heartbeatMs`의 10초다.

```json
{"type":"PONG"}
```

사용자가 `QUIT`을 보내거나 연결이 끊기면 남은 사용자에게 `PLAYER_LEFT`를 보낸다. 진행 중이었다면 라운드를 중단하고 남은 사용자의 화면은 `LOBBY`로 돌아간다.

```json
{"type":"PLAYER_LEFT","playerId":2}
```

서버는 30초 동안 아무 요청을 받지 못한 연결을 종료할 수 있다. 연결 종료 후 새 사용자가 들어오면 빈자리에 새 `playerId`로 접속할 수 있다.

## 5. 오류 응답

요청을 처리할 수 없으면 서버는 아래 형식으로 응답한다. 오류 뒤에도 정상적인 오류라면 연결은 유지한다.

```json
{"type":"ERROR","code":"ROLE_TAKEN"}
```

| 오류 코드 | 상황 |
| --- | --- |
| `SERVER_FULL` | 이미 두 명이 접속해 있어 새 연결을 거절함. 이 응답 뒤 연결 종료 |
| `BAD_COMMAND` | 빈 명령, 지원하지 않는 명령, 인자 수·형식 오류 |
| `INVALID_ROLE` | `PACMAN`, `GHOST` 이외의 역할 요청 |
| `ROLE_TAKEN` | 다른 사용자가 이미 선택한 역할 요청 |
| `INVALID_PHASE` | 현재 상태에서 사용할 수 없는 `READY`, `MOVE` 요청 |

명령이 256자를 넘거나 짧은 시간에 지나치게 많이 들어오면 서버는 해당 연결을 종료할 수 있다. 종료 사유는 서버 로그에 남긴다.

## 6. 메시지 경계 확인 방법

TCP에서는 아래 두 경우가 모두 가능하다.

```text
첫 번째 write: "ROLE PAC"
두 번째 write: "MAN\nREADY\n"
```

위처럼 한 명령이 나뉘어 도착해도 서버는 `ROLE PACMAN` 한 줄을 받은 뒤 `READY` 한 줄을 받아야 한다.

```text
한 번의 write: "MOVE LEFT\nMOVE UP\n"
```

위처럼 두 명령이 한 번에 도착해도 서버는 두 줄을 순서대로 처리해야 한다. 그래서 송신은 `write()` → `newLine()` → `flush()`, 수신은 반복문 안의 `readLine()`으로 구현한다.

통신 기능을 구현할 때는 최소한 아래를 확인한다.

1. 사용자 두 명이 각각 `WELCOME`과 `LOBBY`를 받는다.
2. 같은 역할 선택은 `ROLE_TAKEN`으로 거절된다.
3. 나뉜 명령과 붙은 명령이 각각 한 줄 단위로 처리된다.
4. 탐지 범위 밖 상대는 `GAME_STATE.opponent`에 포함되지 않는다.
5. 세 번째 사용자는 `SERVER_FULL`을 받은 뒤 종료된다.

## 7. 구현 범위 연결

- #7은 `WELCOME`, `LOBBY`, `PLAYER_LEFT`, `PING`·`PONG`, `SERVER_FULL`을 먼저 구현한다.
- #9와 #10은 `ROLE`, `READY`, `GAME_START`와 대기 화면을 구현한다.
- #12와 #13은 `MOVE`, `GAME_STATE`와 방향키·캐릭터 표시를 구현한다.
- #14~#23은 먹이·파워·탐지·결과·역할 교대·상대 종료 메시지를 순서대로 구현한다.

구현을 시작할 때 이 문서와 차이가 생기면, 코드만 바꾸지 말고 메시지 이름·필드·예시를 함께 갱신한다.

# 접속과 역할 선택 통신 규칙

TCP와 UTF-8을 사용한다. 클라이언트는 명령 뒤에 줄바꿈을 붙이고 flush한다. 서버는 JSON 한 줄로 응답한다. 받는 쪽에서는 `readLine()`을 사용한다.

## 접속

서버 주소와 포트에 연결하면 접속 번호를 받는다. 아래 번호는 예시다.

```json
{"type":"WELCOME","protocol":1,"playerId":1,"heartbeatMs":10000}
```

이어서 현재 접속 목록이 온다. `role`이 null이면 역할 선택 전이다.

```json
{"type":"LOBBY","players":[{"id":1,"role":null},{"id":2,"role":null}]}
```

두 명이 이미 접속해 있으면 아래 메시지를 보낸 뒤 연결을 종료한다.

```json
{"type":"ERROR","code":"SERVER_FULL"}
```

## 역할 선택

각 사용자가 원하는 역할을 보낸다.

```text
ROLE PACMAN
```

```text
ROLE GHOST
```

성공하면 양쪽에 갱신된 LOBBY를 보낸다. 본인의 playerId로 역할을 확인하면 된다.

```json
{"type":"LOBBY","players":[{"id":1,"role":"PACMAN"},{"id":2,"role":"GHOST"}]}
```

상대가 이미 선택한 역할이면 `ROLE_TAKEN`, 팩맨·유령 이외의 값이면 `INVALID_ROLE` 오류를 보낸다. 오류가 나면 기존 선택은 유지된다. 비어 있는 역할로는 다시 선택할 수 있다.

## 접속 유지와 종료

10초마다 `PING`을 보내면 `{"type":"PONG"}`을 받는다. 30초간 수신 활동이 없으면 연결을 정리한다.

`QUIT`을 보내거나 연결을 끊으면 남은 사용자에게 종료 안내와 갱신된 LOBBY를 차례로 보낸다.

```json
{"type":"PLAYER_LEFT","playerId":2}
```

새 사용자가 빈자리에 들어오면 새 접속 번호를 받는다. 퇴장한 사용자의 역할은 다시 선택할 수 있다.

## 입력 오류

```json
{"type":"ERROR","code":"BAD_COMMAND"}
```

현재 지원 명령은 ROLE, PING, QUIT이다. 인자 개수가 틀리거나 다른 명령을 보내면 BAD_COMMAND로 응답한다. 오류 응답 뒤에도 연결은 유지한다.

명령은 대문자로 보낸다. 줄 길이는 256자, 입력 횟수는 연결당 초당 120개까지다. 이를 넘거나 송신 대기열이 가득 차면 해당 연결을 종료한다.

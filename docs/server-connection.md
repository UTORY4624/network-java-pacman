# 서버 접속 기능 실행 안내

서버 게임 상태 초기화와 #12 연결 지점은 [게임 상태 안내](server-game-state.md)를 참고한다.

## 이번에 구현한 내용

- 서버에 최대 두 명 접속, 세 번째 접속 거절
- 팩맨·유령 역할 선택 명령과 같은 역할 중복 선택 방지
- 접속 종료 시 인원과 역할 정리, 빈자리 재접속
- 서버 주소·포트 입력 후 버튼으로 접속하는 Swing 화면
- 접속 중 취소, 연결 해제, 입력 오류·접속 실패·인원 초과 안내

접속 후 역할 선택·준비 화면과 게임 시작 안내까지 표시한다. 실제 맵·캐릭터 화면은 후속 #13·#15 작업이다. 서버 이동·먹이 판정과 상태 전송의 확인 방법은 [서버 이동·판정 안내](server-gameplay.md)를 참고한다.

`LOBBY`는 접속 사용자·역할과 `phase`, `ready`를 보낸다. 양측 준비 후 `GAME_START`, `GAME_STATE`가 순서대로 전송된다.

## Eclipse에서 실행

1. `File → Import → General → Existing Projects into Workspace`에서 `network-java-pacman` 폴더를 가져온다. `Copy projects into workspace`는 체크하지 않는다. 이미 가져온 프로젝트는 `Refresh`한다.
2. 전체 프로젝트 실행 환경은 JDK 21을 권장한다. `Project → Properties → Java Build Path → Libraries`에서 사용할 JRE를 확인한다. 추가한 통신 코드만 따로 실행할 때는 JDK 8 이상도 사용할 수 있다.
3. `PacmanServer.launch`를 우클릭해 `Run As`에서 실행한다. 또는 `src/java → pacman.server → PacmanServer.java`를 우클릭해 `Run As → Java Application`을 선택한다.
4. 콘솔에 `서버 시작 / 포트 5555`가 나오는지 확인한다.
5. `PacmanConnect.launch`를 실행한다. 또는 `src/java → pacman.client → ConnectionWindow.java`를 `Java Application`으로 실행한다.
6. 같은 PC에서 확인할 때는 주소 `127.0.0.1`, 포트 `5555`로 두고 **접속하기**를 누른다. `서버에 접속했어요.`가 나오면 성공이다.

접속 창을 두 번 실행하면 두 명 접속을 확인할 수 있다. 세 번째 창에서는 인원 초과 안내가 나온다. 한 창에서 **연결 해제**를 누르면 세 번째 창에서 다시 접속할 수 있다.

다른 PC에서는 같은 네트워크에 연결한 뒤 서버 PC의 내부 IP를 입력한다. 연결되지 않으면 서버 실행 여부, IP·포트와 서버 PC의 방화벽 설정을 확인한다. 서버는 기본적으로 5555 포트로 연결을 기다린다.

## 터미널에서 실행

아래 명령은 저장소 폴더에서 실행한다. JDK의 `java`, `javac`, `jar`가 PATH에 있어야 한다. `server.ps1`은 `src/java/pacman`의 통신 코드만 빌드한다. 서버·접속 창·테스트는 각각 `build/server`, `build/client`, `build/tests`를 사용한다.

```powershell
.\server.ps1 -Task run
```

다른 터미널을 열고 접속 창을 실행한다.

```powershell
.\server.ps1 -Task connect
```

포트를 바꾸려면 서버를 `-Port 5556`처럼 실행하고 접속 창에도 같은 포트를 입력한다. 서버는 `Ctrl+C`로 종료한다.

## 확인 방법

```powershell
.\server.ps1 -Task test
.\server.ps1 -Task test-ui
```

- `test`: 실제 TCP 소켓으로 2인 접속, 인원 초과, 역할 선택, 메시지 경계, 연결 종료, 재접속, 접속 유지 등을 확인한다.
- `test-ui`: Swing 버튼 동작과 입력 검사, 취소 후 재접속, 서버 종료 후 화면 상태를 확인한다. 데스크톱 환경에서 실행한다.
- Eclipse에서는 `src/test/java`의 `ServerTests`, `ConnectionTests`, `ConnectionWindowTests`를 각각 Java Application으로 실행해도 된다. 외부 테스트 라이브러리는 필요 없다.

## 코드 위치와 활용 범위

- `src/java/pacman/server/PacmanServer.java`: 접속 목록과 역할 관리
- `src/java/pacman/server/ClientSession.java`: 사용자별 메시지 송수신과 연결 종료
- `src/java/pacman/server/Json.java`: 서버 응답을 JSON 문자열로 변환
- `src/java/pacman/client/ServerConnection.java`: 접속 요청, 응답 수신, 접속 유지
- `src/java/pacman/client/ConnectionWindow.java`: 주소·포트 입력과 접속 버튼

기존 `src/java/game`과 `src/resources`는 [lucasvigier/pacman](https://github.com/lucasvigier/pacman)을 활용한 원본 게임 코드와 리소스이며, 원본 LICENSE를 유지한다. 이번에 추가한 `pacman.server`, `pacman.client`와 테스트는 네트워크 프로젝트용 구현이다.

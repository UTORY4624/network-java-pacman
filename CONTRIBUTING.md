# 팀 GitHub 협업 안내

저장소: [UTORY4624/network-java-pacman](https://github.com/UTORY4624/network-java-pacman)

기존 1인용 팩맨을 Java 소켓 기반의 2인 대전 게임으로 확장합니다. 원본 코드는 이미 팀 저장소에 올라가 있습니다.

이 문서는 **저장소 초대를 받고 수락한 팀원**을 위한 안내입니다. 초대받은 GitHub 계정으로 위 저장소에 접속할 수 있는 상태에서 시작합니다. 새 저장소를 만들거나 원본 팩맨을 따로 내려받을 필요는 없습니다.

> 최신 main 받기 → 작업 브랜치 생성 → 코드 수정 → Commit → Push → PR 생성 → 팀원 검토 → Squash and merge

## 1. 처음 참여할 때

이 절차는 처음 한 번만 진행합니다. Git, JDK, Eclipse가 준비되어 있어야 합니다. JDK 버전은 팀원끼리 맞춥니다. GitHub Desktop은 필수가 아닙니다.

터미널에서 다음 명령으로 Git 설치 여부를 확인합니다. 명령을 찾을 수 없다고 나오면 [Git for Windows](https://git-scm.com/downloads/win)를 설치하고 터미널을 다시 엽니다.

```powershell
git --version
```

프로젝트를 보관할 폴더에서 우클릭 → **터미널에서 열기**를 누르고 실행합니다.

```powershell
git clone https://github.com/UTORY4624/network-java-pacman.git
cd network-java-pacman
```

이미 Clone했다면 다시 받을 필요가 없습니다. 커밋 작성자 정보를 본인 정보로 설정합니다.

```powershell
git config user.name "본인 이름"
git config user.email "GitHub에 등록한 이메일"
```

로그인을 요구하면 저장소 초대를 수락한 GitHub 계정으로 인증합니다. `Repository not found`가 나오면 브라우저에서 해당 계정으로 저장소를 열 수 있는지 먼저 확인합니다.

**이후 모든 Git 명령은 내 컴퓨터의 network-java-pacman 폴더에서 연 터미널에 입력합니다.**

### Eclipse에서 프로젝트 가져오기

저장소에 Eclipse 설정이 포함되어 있으므로 기존 프로젝트로 가져옵니다.

1. Eclipse에서 **File → Import…**를 선택합니다.
2. **General → Existing Projects into Workspace → Next**를 선택합니다.
3. **Select root directory → Browse…**에서 Clone한 `network-java-pacman` 폴더를 선택합니다.
4. 목록의 `network-java-pacman`을 체크합니다.
5. **Copy projects into workspace**는 체크하지 않습니다. Clone한 폴더를 그대로 사용해야 터미널과 Eclipse에서 같은 파일을 수정하게 됩니다.
6. **Finish**를 누릅니다. 이미 이 프로젝트를 가져왔다면 다시 가져올 필요는 없습니다.

### 원본 게임 실행 확인

1. 왼쪽 **Package Explorer**에서 `network-java-pacman → src/java → game`을 펼칩니다.
2. `GameLauncher.java`를 우클릭합니다.
3. **Run As → Java Application**을 선택합니다.
4. 게임 창이 열리고 방향키로 조작되는지 확인합니다.

이미지와 맵은 `src/resources`에 있습니다. `ImageIO.read`에서 `input == null!` 오류가 나면 프로젝트 우클릭 → **Properties → Java Build Path → Source**에 `src/java`와 `src/resources`가 모두 있는지 확인합니다. 빠진 폴더는 **Add Folder…**로 추가합니다. 이후 **Refresh**, **Project → Clean…**을 실행하고 다시 시도합니다.

## 2. 새 작업 시작하기

현재 브랜치와 변경 파일을 확인합니다.

```powershell
git status
```

미완료 변경이 없다면 최신 main을 받고 작업 브랜치를 만듭니다. 변경이 있다면 현재 작업 브랜치에서 먼저 커밋하거나 팀원과 처리 방법을 확인합니다.

```powershell
git switch main
git pull --ff-only
git switch -c feature/server-connect
```

`Already up to date.`는 이미 최신이라는 뜻입니다. `--ff-only` 오류가 나면 로컬과 원격 main의 이력이 갈라졌을 수 있으므로 강제로 덮어쓰지 말고 함께 확인합니다.

| 작업 | 브랜치 이름 예시 |
| --- | --- |
| 서버 접속 | `feature/server-connect` |
| 대기 화면 | `feature/waiting-screen` |
| 이동 동기화 | `feature/movement-sync` |
| 충돌 오류 수정 | `fix/collision` |

**main에 직접 수정하거나 Push하지 않고 작업 브랜치에서 개발합니다.** 브랜치 이름은 작업마다 바꿉니다.

이미 진행 중인 작업을 이어갈 때는 기존 브랜치로 이동합니다. `-c`는 새 브랜치를 만들 때만 사용합니다.

```powershell
git switch feature/server-connect
```

터미널에서 Pull하거나 브랜치를 바꾼 뒤에는 Eclipse에서 프로젝트 우클릭 → **Refresh**로 파일을 갱신합니다. Eclipse에서 수정 중인 파일은 전환 전에 저장하고 Git 변경 상태를 확인합니다.

## 3. 수정 후 Commit과 Push

Eclipse에서 파일을 저장하고 수정한 기능을 실행해 확인합니다.

```powershell
git status
git diff
git add .
git diff --cached
git commit -m "서버 접속 기능 구현"
git push -u origin feature/server-connect
```

브랜치 이름과 커밋 메시지는 실제 작업에 맞게 바꿉니다.

- `git status`: 현재 브랜치와 변경 파일 확인
- `git diff`: 추적 중인 파일의 변경 확인. 새 파일은 직접 열어 확인
- `git add .`: 현재 폴더 아래의 변경을 커밋 대상으로 추가
- `git diff --cached`: 커밋에 들어갈 내용 확인
- `git commit`: 내 컴퓨터에 변경 기록
- `git push`: GitHub의 해당 브랜치에 기록 업로드

빌드 결과물인 `*.class`, `bin/` 등은 `.gitignore`로 제외합니다. 불필요한 파일이 포함되지 않았는지 확인합니다.

같은 브랜치에서 추가 작업을 올릴 때는 다음처럼 실행합니다.

```powershell
git add .
git diff --cached
git commit -m "접속 실패 처리 추가"
git push
```

**로컬 파일 수정이나 Commit만으로 GitHub가 바뀌지 않습니다. 작업 브랜치에 Push해도 main은 바뀌지 않습니다.**

## 4. Pull Request 만들기

GitHub에서 **Compare & pull request**를 누릅니다. 버튼이 없으면 **Pull requests → New pull request**로 들어갑니다.

```text
base: main ← compare: feature/server-connect
```

PR은 작업 브랜치의 변경을 main에 합쳐 달라는 요청입니다. 변경 내용과 확인 결과를 적고 팀원에게 검토를 요청합니다.

```text
제목: 서버 접속 기능 구현

변경 내용
- 서버 IP와 포트로 접속
- 접속 실패 안내

확인한 내용
- 서버 실행 중 접속 성공
- 서버가 없을 때 오류 안내 표시
```

## 5. 팀원 검토와 수정

검토자는 **Files changed**에서 코드를 확인합니다.

- 문제가 없으면 **Review changes → Approve**
- 수정이 필요하면 댓글이나 **Request changes**

작성자는 같은 작업 브랜치에서 수정하고 Commit, Push합니다. 기존 PR에 자동으로 추가되므로 PR을 새로 만들 필요가 없습니다.

## 6. Squash and merge

검토와 수정이 끝나면 **Squash and merge**를 선택합니다. 브랜치의 여러 커밋을 하나로 묶어 main에 반영합니다. 최종 커밋 메시지를 확인하고 확정합니다.

머지 완료 후 GitHub의 **Delete branch**로 원격 작업 브랜치를 삭제합니다. 자동 삭제 설정이 켜져 있으면 이미 삭제되어 있을 수 있습니다. 원격 브랜치를 삭제해도 로컬 브랜치는 남습니다. main 브랜치는 삭제하지 않습니다.

선택지가 없으면 관리자가 **Settings → General → Pull Requests → Allow squash merging** 설정을 확인합니다.

## 7. 머지 후 다음 작업

로컬 변경을 정리하고 최신 main에서 새 브랜치를 만듭니다.

```powershell
git switch main
git pull --ff-only
git switch -c feature/role-select
```

**스쿼시 머지한 브랜치는 재사용하지 않습니다.** 다음 작업마다 최신 main에서 새 브랜치를 만듭니다.

로컬의 지난 브랜치는 잠시 남아 있어도 됩니다. 정리하려면 먼저 GitHub에서 해당 PR이 **Merged**인지, 로컬 브랜치에 PR 이후 추가한 커밋이나 미완료 변경이 없는지 확인합니다. 스쿼시 머지 후에는 이력이 달라 일반 삭제(`-d`)가 거절될 수 있습니다.

다음 명령은 `feature/server-connect` 작업이 머지되었고 추가 변경이 없음을 확인한 뒤에만 실행합니다. 삭제할 브랜치에 머물러 있다면 먼저 main으로 이동하고 최신 코드를 받습니다.

```powershell
git switch main
git pull --ff-only
git branch -D feature/server-connect
git fetch --prune
```

`-D`는 로컬 브랜치를 강제로 삭제합니다. 작업이 반영되었는지 확실하지 않으면 삭제하지 말고 팀원과 확인합니다. `git fetch --prune`은 GitHub에서 이미 삭제된 브랜치의 로컬 추적 정보를 정리합니다.

## 8. 작업 중 main의 변경 가져오기

다른 팀원의 PR이 먼저 머지되었다면 현재 작업을 먼저 커밋하고, 작업 브랜치에서 실행합니다.

```powershell
git fetch origin
git merge origin/main
```

충돌 없이 끝나면 동작을 확인하고 `git push`합니다.

충돌이 발생하면 파일의 양쪽 변경을 비교해 정리하고 충돌 표시를 제거한 뒤 저장합니다. 동작 확인 후 실행합니다.

```powershell
git add .
git commit -m "main 변경 사항 통합"
git push
```

충돌 해결 중 이번 병합을 취소하려면 다음을 실행합니다.

```powershell
git merge --abort
```

해결이 어려우면 강제 Push나 변경 삭제 명령을 실행하지 말고 팀원과 함께 확인합니다.

## 참고

- [GitHub: PR 머지 방식](https://docs.github.com/en/pull-requests/reference/pull-request-merges)
- [Eclipse: 기존 프로젝트 가져오기](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.user/tasks/tasks-importproject.htm)
- [기반 프로젝트: lucasvigier/pacman](https://github.com/lucasvigier/pacman)

원본 LICENSE를 유지하고 원본 활용 범위와 직접 구현한 기능을 기록합니다.

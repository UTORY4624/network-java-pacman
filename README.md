## 네트워크 프로젝트 실행

서버 접속 관리·역할 선택 처리와 Swing 접속 화면을 추가했습니다. Eclipse 실행 방법은 [접속 기능 실행 안내](docs/server-connection.md), 메시지 형식은 [통신 규칙](docs/protocol.md)을 참고해주세요.

원본 게임은 `src/java/game`, 추가한 통신 코드는 `src/java/pacman`에 있습니다. 아래는 원본 프로젝트 안내입니다.

<h1 align="center">
  <br>
  <a href=""><img src="https://i.pinimg.com/originals/b4/ee/c4/b4eec4d093adbe9d8a3cbb40d024836a.png" width="450"></a>
  <br>
  <br>
</h1>

## Table of contents
* [General info](#general-info)
* [Technologies](#technologies)
* [Setup](#setup)
___
## General info
Good ol' PacMan.

To play, you just need to use the arrow keys on your keyboard ◀️ ▶️ 🔼 🔽 (or with 🇿 🇶 🇸 🇩).
Avoid traps, eat some super pac gums and chase the 👻 to eat them.
<br />
What's the best score you can get ?

Let's find out !
___
## Technologies

Made with ❤️ in Java.
<br />
Runs using _java.swing_ library and the **Factory**, **State** and **Strategy** design patterns.
___
## Setup

Prerequisites: You must have [Java](https://www.java.com/en/download/) installed (JRE 16 at least) on your computer.

1. On GitHub, go to the main page of the [repository](https://github.com/lucasvigier/pacman).
2. Go to the ``Releases`` [section](https://github.com/lucasvigier/pacman/releases/tag/production), and download the file ``pacman.jar``
3. Run the game with the command :

    ```
    java -jar pacman.jar
    ```
    (*Or just double click the file on Windows*)
4. Enjoy !

___
## License
This project is licensed under the MIT License - see the [LICENSE](https://github.com/lucasvigier/pacman/blob/main/LICENSE) file for more details.

<h1 align="center">
  <a href=""><img src="https://res.cloudinary.com/dek4evg4t/image/upload/v1562055192/pac-man-logo-icon-512x512.png" width="50"></a>
  <br>
</h1>

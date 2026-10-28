# LibraryFlow — Java Library Management System

A polished, responsive library management application with a Java HTTP-server backend and a browser-based interface. It runs locally on Windows and needs no database server, Maven, npm, or third-party Java libraries.

## Requirements
- JDK 17 or newer (not only a JRE)
- Windows 10 or Windows 11
- A modern browser (Edge, Chrome, or Firefox)

## Run on Windows
1. Download and extract `LibraryFlow.zip` to a normal folder, such as `Documents\LibraryFlow`.
2. Install JDK 17 or newer if Java is not installed. In Command Prompt, `java -version` and `javac -version` should both work.
3. Double-click **`run.bat`**.
4. Wait until the console says `LibraryFlow is running at http://127.0.0.1:8081`.
5. Open that address in your browser.
6. Keep the black console window open while using the application. Press `Ctrl+C` in it to stop the server.

If port 8081 is already in use, close the other application using it or change `PORT` near the top of `src\LibraryFlowServer.java`, then run `run.bat` again.

## Features
- Dashboard with total titles, total copies, active loans, and overdue count
- Book inventory: create, edit, delete, search, category/availability filters
- Unique ISBN validation and quantity validation
- Borrowing workflow with borrower name and due date
- Return workflow and overdue status detection
- Prevents issuing unavailable books, deleting books with active loans, and reducing quantity below active loans
- CSV inventory export
- Responsive layout for desktop and mobile
- Sample book records on first launch
- Persistent local data in `books_db.txt` and `loans_db.txt`
- No login or internet connection required for core functionality

## Java concepts demonstrated
- Classes and objects (`Book`, `Loan`)
- Encapsulation through model classes and server operations
- Collections (`Map`, `List`, streams)
- Exception handling (`try/catch`)
- File handling and persistent storage (`Files`, `Path`, Base64 encoding)
- Networking and client-server architecture (`HttpServer`, HTTP requests)
- Multithreading using a thread pool for HTTP request handling
- Input validation and modular methods

## Project structure
```
LibraryFlow/
├── run.bat
├── README.md
├── src/
│   └── LibraryFlowServer.java
└── web/
    ├── index.html
    ├── styles.css
    └── app.js
```

## Data notes
The first run creates a small sample inventory. Records are stored locally in text files beside the project and remain after the app is closed. To reset the demo data, stop the server and delete `books_db.txt` and `loans_db.txt`; the sample book inventory will be recreated on the next launch. Deleting those files permanently removes locally stored records. This is a coursework demonstration, not a secured public-facing library system.

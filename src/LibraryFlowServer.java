import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

public class LibraryFlowServer {
    private static final int PORT = 8081;
    private static final Path BOOK_FILE = Paths.get("books_db.txt");
    private static final Path LOAN_FILE = Paths.get("loans_db.txt");
    private static final Path WEB = Paths.get("web");
    private static final Map<String, Book> books = new LinkedHashMap<>();
    private static final Map<String, Loan> loans = new LinkedHashMap<>();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    static class Book {
        String id, title, author, category, isbn;
        int quantity;
        Book(String id, String title, String author, String category, String isbn, int quantity) {
            this.id=id; this.title=title; this.author=author; this.category=category; this.isbn=isbn; this.quantity=quantity;
        }
        Map<String,Object> map() {
            Map<String,Object> m=new LinkedHashMap<>(); m.put("id",id);m.put("title",title);m.put("author",author);
            m.put("category",category);m.put("isbn",isbn);m.put("quantity",quantity);m.put("available",available(id)); return m;
        }
    }
    static class Loan {
        String id, bookId, borrower, issued, due, returned;
        Loan(String id,String bookId,String borrower,String issued,String due,String returned) {
            this.id=id;this.bookId=bookId;this.borrower=borrower;this.issued=issued;this.due=due;this.returned=returned;
        }
        Map<String,Object> map() {
            Map<String,Object> m=new LinkedHashMap<>(); Book b=books.get(bookId);
            m.put("id",id);m.put("bookId",bookId);m.put("bookTitle",b==null?"Deleted book":b.title);m.put("borrower",borrower);
            m.put("issued",issued);m.put("due",due);m.put("returned",returned);m.put("status",returned.isEmpty()?(due.compareTo(LocalDate.now().toString())<0?"Overdue":"Borrowed"):"Returned"); return m;
        }
    }

    public static void main(String[] args) throws Exception {
        loadBooks(); loadLoans(); seedIfEmpty();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",PORT),0);
        server.createContext("/api/", LibraryFlowServer::api);
        server.createContext("/", LibraryFlowServer::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(8)); server.start();
        System.out.println("LibraryFlow is running at http://127.0.0.1:"+PORT);
        System.out.println("Keep this window open while using the app. Press Ctrl+C to stop.");
    }

    private static synchronized void api(HttpExchange x) throws IOException {
        headers(x,"application/json; charset=utf-8");
        String method=x.getRequestMethod(), path=x.getRequestURI().getPath();
        try {
            if(path.equals("/api/summary") && method.equals("GET")) {
                int total=books.values().stream().mapToInt(b->b.quantity).sum();
                int borrowed=(int)loans.values().stream().filter(l->l.returned.isEmpty()).count();
                int overdue=(int)loans.values().stream().filter(l->l.returned.isEmpty()&&l.due.compareTo(LocalDate.now().toString())<0).count();
                send(x,200,json(Map.of("titles",books.size(),"copies",total,"borrowed",borrowed,"overdue",overdue)));return;
            }
            if(path.equals("/api/books") && method.equals("GET")) {send(x,200,json(books.values().stream().map(Book::map).toList()));return;}
            if(path.equals("/api/books") && method.equals("POST")) {
                Map<String,String> f=form(x); String title=val(f,"title"), author=val(f,"author"), category=val(f,"category");
                int qty=integer(val(f,"quantity"),-1);
                if(title.isBlank()||author.isBlank()||category.isBlank()||qty<1){send(x,400,error("Enter a title, author, category and quantity of at least 1."));return;}
                String isbn=val(f,"isbn");
                if(!isbn.isBlank()&&books.values().stream().anyMatch(b->b.isbn.equalsIgnoreCase(isbn))){send(x,409,error("A book with this ISBN already exists."));return;}
                String id=nextBookId(); Book b=new Book(id,title,author,category,isbn,qty);books.put(id,b);saveBooks();send(x,201,json(b.map()));return;
            }
            if(path.startsWith("/api/books/")) {
                String id=decode(path.substring("/api/books/".length())); Book b=books.get(id);
                if(b==null){send(x,404,error("Book not found."));return;}
                if(method.equals("PUT")) {
                    Map<String,String> f=form(x);String title=val(f,"title"),author=val(f,"author"),category=val(f,"category"),isbn=val(f,"isbn");int qty=integer(val(f,"quantity"),-1);
                    long active=loans.values().stream().filter(l->l.bookId.equals(id)&&l.returned.isEmpty()).count();
                    if(title.isBlank()||author.isBlank()||category.isBlank()||qty<1||qty<active){send(x,400,error("Check the required fields. Quantity cannot be less than the number currently borrowed."));return;}
                    if(!isbn.isBlank()&&books.values().stream().anyMatch(other->!other.id.equals(id)&&other.isbn.equalsIgnoreCase(isbn))){send(x,409,error("A book with this ISBN already exists."));return;}
                    b.title=title;b.author=author;b.category=category;b.isbn=isbn;b.quantity=qty;saveBooks();send(x,200,json(b.map()));return;
                }
                if(method.equals("DELETE")) {
                    if(loans.values().stream().anyMatch(l->l.bookId.equals(id)&&l.returned.isEmpty())){send(x,409,error("This book has active loans. Process the returns before deleting it."));return;}
                    books.remove(id);saveBooks();send(x,200,"{\"ok\":true}");return;
                }
            }
            if(path.equals("/api/loans") && method.equals("GET")){send(x,200,json(loans.values().stream().map(Loan::map).toList()));return;}
            if(path.equals("/api/loans") && method.equals("POST")) {
                Map<String,String> f=form(x);String bookId=val(f,"bookId"), borrower=val(f,"borrower"), due=val(f,"due");Book b=books.get(bookId);
                if(b==null||borrower.isBlank()||!validDate(due)){send(x,400,error("Choose a book, enter the borrower's name and select a valid due date."));return;}
                if(available(bookId)<1){send(x,409,error("No copies of this book are currently available."));return;}
                if(due.compareTo(LocalDate.now().toString())<0){send(x,400,error("The due date cannot be in the past."));return;}
                Loan l=new Loan(nextLoanId(),bookId,borrower,LocalDate.now().toString(),due,"");loans.put(l.id,l);saveLoans();send(x,201,json(l.map()));return;
            }
            if(path.startsWith("/api/loans/")&&path.endsWith("/return")&&method.equals("POST")) {
                String id=decode(path.substring("/api/loans/".length(),path.length()-"/return".length()));Loan l=loans.get(id);
                if(l==null){send(x,404,error("Loan record not found."));return;}
                if(!l.returned.isEmpty()){send(x,409,error("This book has already been returned."));return;}
                l.returned=LocalDate.now().toString();saveLoans();send(x,200,json(l.map()));return;
            }
            if(path.equals("/api/health")){send(x,200,"{\"ok\":true}");return;}
            send(x,404,error("API endpoint not found."));
        } catch(Exception e){send(x,500,error("Something went wrong: "+(e.getMessage()==null?"unexpected error":e.getMessage())));}
    }

    private static int available(String id){Book b=books.get(id);if(b==null)return 0;long active=loans.values().stream().filter(l->l.bookId.equals(id)&&l.returned.isEmpty()).count();return b.quantity-(int)active;}
    private static String nextBookId(){int n=1;while(books.containsKey(String.format("BK-%03d",n)))n++;return String.format("BK-%03d",n);}
    private static String nextLoanId(){int n=1;while(loans.containsKey(String.format("LN-%03d",n)))n++;return String.format("LN-%03d",n);}
    private static boolean validDate(String s){try{LocalDate.parse(s);return true;}catch(Exception e){return false;}}
    private static int integer(String s,int fallback){try{return Integer.parseInt(s);}catch(Exception e){return fallback;}}
    private static String val(Map<String,String> m,String k){return m.getOrDefault(k,"").trim();}
    private static String decode(String s){try{return URLDecoder.decode(s,StandardCharsets.UTF_8);}catch(Exception e){return s;}}
    private static Map<String,String> form(HttpExchange x)throws IOException{
        String body=new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);Map<String,String> m=new HashMap<>();
        for(String part:body.split("&")){String[] kv=part.split("=",2);if(kv.length==2)m.put(decode(kv[0]),decode(kv[1].replace("+"," ")));}return m;
    }
    private static void staticFile(HttpExchange x)throws IOException{
        String path=x.getRequestURI().getPath();if(path.equals("/"))path="/index.html";Path file=WEB.resolve(path.substring(1)).normalize();
        if(!file.startsWith(WEB.normalize())||!Files.isRegularFile(file)){sendRaw(x,404,"text/plain; charset=utf-8","Page not found");return;}
        String type=file.toString().endsWith(".css")?"text/css; charset=utf-8":file.toString().endsWith(".js")?"application/javascript; charset=utf-8":"text/html; charset=utf-8";
        sendRaw(x,200,type,Files.readString(file,StandardCharsets.UTF_8));
    }
    private static void headers(HttpExchange x,String type){x.getResponseHeaders().set("Content-Type",type);x.getResponseHeaders().set("Cache-Control","no-store");}
    private static void send(HttpExchange x,int status,String body)throws IOException{byte[] b=body.getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(status,b.length);try(OutputStream out=x.getResponseBody()){out.write(b);}}
    private static void sendRaw(HttpExchange x,int status,String type,String body)throws IOException{x.getResponseHeaders().set("Content-Type",type);send(x,status,body);}
    private static String error(String s){return json(Map.of("error",s));}
    private static String json(Object o){if(o==null)return "null";if(o instanceof String s)return "\""+escape(s)+"\"";if(o instanceof Number||o instanceof Boolean)return o.toString();if(o instanceof Map<?,?> m){List<String> a=new ArrayList<>();m.forEach((k,v)->a.add(json(String.valueOf(k))+":"+json(v)));return "{"+String.join(",",a)+"}";}if(o instanceof Iterable<?> it){List<String>a=new ArrayList<>();for(Object v:it)a.add(json(v));return "["+String.join(",",a)+"]";}return json(o.toString());}
    private static String escape(String s){return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");}
    private static String enc(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    private static String dec(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    private static void loadBooks()throws IOException{if(!Files.exists(BOOK_FILE))return;for(String line:Files.readAllLines(BOOK_FILE)){String[] p=line.split("\\t",-1);if(p.length!=6)continue;try{Book b=new Book(dec(p[0]),dec(p[1]),dec(p[2]),dec(p[3]),dec(p[4]),Integer.parseInt(p[5]));books.put(b.id,b);}catch(Exception ignored){}}}
    private static void loadLoans()throws IOException{if(!Files.exists(LOAN_FILE))return;for(String line:Files.readAllLines(LOAN_FILE)){String[] p=line.split("\\t",-1);if(p.length!=6)continue;try{Loan l=new Loan(dec(p[0]),dec(p[1]),dec(p[2]),dec(p[3]),dec(p[4]),dec(p[5]));loans.put(l.id,l);}catch(Exception ignored){}}}
    private static void saveBooks()throws IOException{List<String> lines=new ArrayList<>();for(Book b:books.values())lines.add(enc(b.id)+"\t"+enc(b.title)+"\t"+enc(b.author)+"\t"+enc(b.category)+"\t"+enc(b.isbn)+"\t"+b.quantity);Files.write(BOOK_FILE,lines,StandardCharsets.UTF_8);}
    private static void saveLoans()throws IOException{List<String> lines=new ArrayList<>();for(Loan l:loans.values())lines.add(enc(l.id)+"\t"+enc(l.bookId)+"\t"+enc(l.borrower)+"\t"+enc(l.issued)+"\t"+enc(l.due)+"\t"+enc(l.returned));Files.write(LOAN_FILE,lines,StandardCharsets.UTF_8);}
    private static void seedIfEmpty()throws IOException{if(!books.isEmpty())return;String[][] sample={{"The Alchemist","Paulo Coelho","Fiction","9780061122415","5"},{"Clean Code","Robert C. Martin","Technology","9780132350884","3"},{"Atomic Habits","James Clear","Self Growth","9780735211292","4"},{"The Hobbit","J. R. R. Tolkien","Fantasy","9780547928227","2"},{"Java: The Complete Reference","Herbert Schildt","Technology","9781260440232","4"},{"Ikigai","Hector Garcia","Wellness","9780143130727","3"}};for(String[] s:sample){String id=nextBookId();books.put(id,new Book(id,s[0],s[1],s[2],s[3],Integer.parseInt(s[4])));}saveBooks();}
}

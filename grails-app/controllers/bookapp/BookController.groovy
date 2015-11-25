package bookapp

import grails.converters.JSON
import org.json.simple.JSONArray
import org.json.simple.JSONObject

import java.text.DateFormat
import java.text.SimpleDateFormat

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional

@Transactional(readOnly = true)
class BookController {

    def BookHelperService bookHelperService;
    def PushNotificationService ;

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond Book.list(params), model:[bookInstanceCount: Book.count()]
    }

    def show(Book bookInstance) {
        respond bookInstance
    }

    def create() {
        respond new Book(params)
    }

    @Transactional
    def save(Book bookInstance) {
        if (bookInstance == null) {
            notFound()
            return
        }

        if (bookInstance.hasErrors()) {
            respond bookInstance.errors, view:'create'
            return
        }

        bookInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'bookInstance.label', default: 'Book'), bookInstance.id])
                redirect bookInstance
            }
            '*' { respond bookInstance, [status: CREATED] }
        }
    }

    def edit(Book bookInstance) {
        respond bookInstance
    }

    @Transactional
    def update(Book bookInstance) {
        if (bookInstance == null) {
            notFound()
            return
        }

        if (bookInstance.hasErrors()) {
            respond bookInstance.errors, view:'edit'
            return
        }

        bookInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'Book.label', default: 'Book'), bookInstance.id])
                redirect bookInstance
            }
            '*'{ respond bookInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(Book bookInstance) {

        if (bookInstance == null) {
            notFound()
            return
        }

        bookInstance.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'Book.label', default: 'Book'), bookInstance.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'bookInstance.label', default: 'Book'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    //http://localhost:8080/bookApp/book/create_book?title=History&token=12125244
    @Transactional
    def create_book(){

        println(params)

        JSONObject obj = new JSONObject();
        JSONObject responseObj = new JSONObject();
        try{
            def user = Book.getUserIDByToken(params.token)
            println user
            Book book = new Book(params)
            book.user=user;
            book.category = Category.findById(params.categoryId)

            if(request.getFile('image')){
                def uploadedFile = request.getFile('image')
                def webRootDir = servletContext.getRealPath("/")
                println "webRootDir: " + webRootDir
                def userDir = new File(webRootDir, "/images/books")
                println "userDir: " + userDir
                userDir.mkdirs()
                String fileName = Book.getTimeStamp()+user.userToken+".jpg"
                uploadedFile.transferTo(new File(userDir, fileName))
                book.imageUrl=fileName
            }


            if(book.save( flush:true, failOnError: true )){

                responseObj.put("bookId", book.getId())
                responseObj.put("message", "book created");
                obj.put("success", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            e.printStackTrace()
            responseObj.put("message", "failed to create book");
            responseObj.put("exception", e.getMessage());
            obj.put("error", responseObj)
            render obj as JSON
        }
    }

    @Transactional
    def markComplete(){

        JSONObject obj = new JSONObject();
        JSONObject responseObj = new JSONObject();

        try{

            def book = Book.findById(params.bookId)
            book.isCompleted = true;

            if(book.save(flush: true, failOnError: true)){
                responseObj.put("message", "book status complete");
                obj.put("success", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            responseObj.put("message", "failed");
            responseObj.put("exception", e.getMessage());
            obj.put("success", responseObj)
            render obj as JSON
        }
    }

    //http://localhost:8080/bookApp/book/fetchUserBook?token=12125244
    //Tested
    def fetchUserBook(){
        HashMap jsonMap = new HashMap()

        try {

            UserTable user = Book.getUserIDByToken(params.token);
            def book
            if(params.offset){
                def offset = params.offset * 10;
                book = Book.findAllByUser(user ,[max: 10, offset: offset]);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON

            }else {
                book = Book.findAllByUser(user);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON

            }

        }catch (Exception e){
            println "error occured: " + e.getMessage()

        }
    }

    //http://localhost:8080/bookApp/book/fetchUserBook?fetchLatestBooks
    //http://localhost:8080/bookApp/book/fetchUserBook?fetchLatestBooks?offset=0
    def fetchLatestBooks(){
        println('in fetchLatestBooks')
        HashMap jsonMap = new HashMap()
        try {
            def book
            if(params.offset){
                String query = "from Book as b where b.isCompleted=:flagComplete order by b.dateCreated desc"
                def offset = Integer.parseInt( params.offset  )* 10;
                book = Book.findAll(query,[flagComplete: false],[ max: 10, offset: offset]);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON
            }else {
                String query = "from Book as b where b.isCompleted=:flagComplete order by b.dateCreated desc"
                book = Book.findAll(query, [flagComplete: false]);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON
            }
        }catch (Exception e){
            println "error occured: " + e.getMessage()
        }
    }

    def fetchBooksByCity(){
        println('in fetchLatestBooks')
        HashMap jsonMap = new HashMap()
        try {

            def books = PickupLocation.findAllByCity(params.city);
            def books1 = [];
            books.each {
                books1.push( Book.findById(it.bookId) )
            }
            render books1 as JSON

        }catch (Exception e){
            println "error occured: " + e.getMessage()
        }
    }

    def fetchLatestByCat(){

        HashMap jsonMap = new HashMap()
        try {
            def book
            Category category = Category.findByName(params.category)
            if(params.offset){
//                String query = "from Book as b where b.category=:category"
                def offset = Integer.parseInt( params.offset  )* 10;
                book = Book.findAllByCategoryAndIsCompleted(category, false, [max: 10, offset: offset]);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON
            }else {
//                String query = "from Book as b where b.category=:category"
                book = Book.findAllByCategoryAndIsCompleted(category, false);
                jsonMap = bookHelperService.getBookHasMap(book)
                render jsonMap as JSON
            }
        }catch (Exception e){
            println "error occured: " + e.getMessage()
        }
    }


    @Transactional
    def save_book_address(){

        println(params)
        JSONObject obj = new JSONObject();
        JSONObject responseObj = new JSONObject();
        try{
            def bookId = Book.findById(params.bookId)
            PickupLocation address = new PickupLocation(params)
            address.book = bookId
            address.user =  Book.getUserIDByToken(params.token)
            if(address.save( flush:true, failOnError: true )){

                responseObj.put("pickupId", address.id );
                responseObj.put("message", "Adress Confirmed");
                obj.put("success", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            e.printStackTrace()
            responseObj.put("message", "failed to Add Address");
            responseObj.put("exception", e.getMessage());
            obj.put("error", responseObj)
            render obj as JSON
        }

    }

    def request_book_address(){
        Book book = Book.findById(params.bookId)
        def addressOfBook = PickupLocation.findByBook(book)

        JSONObject obj = new JSONObject()


        try{

            JSONObject responseObj = new JSONObject();
            responseObj.put("addressOne", addressOfBook.addressOne);
            responseObj.put("addressTwo", addressOfBook.addressTwo);
            responseObj.put("city", addressOfBook.city);
            responseObj.put("bookId", addressOfBook.bookId);
            responseObj.put("latitude", addressOfBook.latitude);
            responseObj.put("longitude", addressOfBook.longitude);
            responseObj.put("mobile", addressOfBook.mobileNumber);


            obj.put("status", "success")
            obj.put("address", responseObj)
            render  obj as JSON;

        }catch (Exception e){
            obj.put("status", "failed")
            obj.put("error", e.getMessage())
            render  obj as JSON;
        }
    }

    //http://localhost:8080/bookApp/book/addToWishList?token=1444739497970&bookRef=23
    @Transactional
    def addToWishList(){

        JSONObject responseObj = new JSONObject();
        JSONObject obj = new JSONObject()

        try {
            UserTable user = Book.getUserIDByToken(params.token);

            WishList address = new WishList()
            address.user = user;
            address.bookRef = params.bookRef;

            if(address.save(flush: true, failOnError: true)){
                responseObj.put("message", "Added To Wishlist");
                obj.put("status", "success")
                obj.put("response", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            responseObj.put("message", "Error");
            obj.put("status", "failed")
            obj.put("response", e.getMessage())
            render obj as JSON
        }
    }

    @Transactional
    def removeWishList(){
        JSONObject responseObj = new JSONObject();
        JSONObject obj = new JSONObject()

        try {
            UserTable user = Book.getUserIDByToken(params.token);

            def wishObject = WishList.findByUserAndBookRef(user, params.bookRef)

            println wishObject

            wishObject.delete(flush: true,  failOnError: true)

            responseObj.put("message", "Removed From Wishlist");
            obj.put("status", "success")
            obj.put("response", responseObj)
            render obj as JSON

        }catch (Exception e){
            responseObj.put("message", "Error");
            obj.put("status", "failed")
            obj.put("response", e.getMessage())
            render obj as JSON
        }

    }

    def getUserWishList(){

        JSONObject booksobject = new JSONObject()
        JSONArray array = new JSONArray()
        def user = Book.getUserIDByToken(params.token);
        println 'Book List'+user
        def wishListInstance = WishList.findAllByUser(user)


        if(wishListInstance){

            for(int i = 0; i< wishListInstance.size(); i++){
                String book= wishListInstance.get(i).getBookRef();
                println("Book :"+book)
                Book bookInstance = Book.findById(book);
                if(bookInstance != null){
                    JSONObject object = bookHelperService.getBookAsJson(bookInstance)
                    array.putAt(i,object)
                }
                println '-----------'+ array
            }
            booksobject.put("books", array)
            render booksobject  as JSON

        }
    }

    //will get bookId, UserId, As Params
    @Transactional
    def generateShareRequest(){

        JSONObject responseObj = new JSONObject();
        JSONObject obj = new JSONObject()

        try {

            Book bookId = Book.findById(params.bookId)
            UserTable user = Book.getUserIDByToken(params.token);

            Request request = new Request()
            request.is_completed = false;
            request.book = bookId;
            request.user = user;
            request.requestToken = user.email


            if(request.save(flush: true,  failOnError: true)){

                if(bookId.shareCount == null){
                    bookId.shareCount = 1;
                }else{
                    bookId.shareCount = bookId.getShareCount() + 1;
                }
                bookId.save( flush: true, failOnError: true)

                sendPushNotification(user.gcm, "requestToken", request.requestToken)
                sendPushNotification(bookId.user.gcm, "notifySeller", ""+user.userName)

                responseObj.put("message", "Request Send to user");
                obj.put("status", "success")
                obj.put("requestToken", request.requestToken)
                obj.put("response", responseObj)
                render obj as JSON
            }


        }catch (Exception e){
            responseObj.put("message", "Fail to send request"+e.getMessage());
            obj.put("status", "fail")
            obj.put("response", responseObj)
            render obj as JSON
        }
    }

    @Transactional
    def confirmBookRequest(){
        print 'params' + params
        JSONObject responseObj = new JSONObject();
        JSONObject obj = new JSONObject()

        try{
            def book = Book.findById(params.bookId)
            def request = null;
            if(book != null){
                request = Request.findByBookAndRequestToken(book, params.requestToken)
                println '-----*'+request;

                request.is_completed = true;
                DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
                Date date = new Date();

                request.sharedDate = dateFormat.format(date)
                request.save(flush: true, failOnError: true)

                book.isCompleted = true;
                book.save(flush: true, failOnError: true)
            }

            if(request == null){
                responseObj.put("message", "doesnt match bookId or token ");
                obj.put("status", "failed")
                obj.put("response", responseObj)
                render obj as JSON
            }else{
                responseObj.put("message", "confirmed");
                obj.put("status", "success")
                obj.put("userDetail", request.user.userToken)
                obj.put("response", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            responseObj.put("message", "doesnt match bookId or token ");
            obj.put("status", "failed")
            obj.put("response", responseObj)
            render obj as JSON
        }
    }


    def sharedBooksRequest(){

        JSONObject booksobject = new JSONObject()
        JSONArray array = new JSONArray()

        def rec =  Book.executeQuery("select b.id, b.title, r.is_completed, r.requestToken, r.user.id, b.endDate, r.sharedDate from Book b ,Request r where b.id = r.book.id and b.id = :bookId", [bookId : Long.parseLong(params.bookId)]);

        println rec
        for(int i=0; i < rec.size(); i++ ){
            HashMap jsonMap = new HashMap()

            jsonMap.put("bookId", rec[i][0] )
            jsonMap.put("title", rec[i][1] )
            jsonMap.put("is_completed", rec[i][2] )
            jsonMap.put("requestToken", rec[i][3] )
            jsonMap.put("userId", rec[i][4] )
            jsonMap.put("SharedDays", rec[i][5] )
            jsonMap.put("sharedOnDate", rec[i][6] )
            array.putAt( i, jsonMap )
        }
        booksobject.put("books", array)
        render booksobject as JSON
    }

    def getMySharedTokens(){
        JSONObject booksobject = new JSONObject()
        JSONArray array = new JSONArray()

        UserTable user  = Book.getUserIDByToken(params.token);

        def rec =  Book.executeQuery("select b.id, b.title, r.is_completed, r.requestToken, r.user.id, b.endDate, r.sharedDate from Book b ,Request r where b.id = r.book.id and r.user.id = :bookId", [bookId : user.id]);

        println rec
        for(int i=0; i < rec.size(); i++ ){
            HashMap jsonMap = new HashMap()

            jsonMap.put("bookId", rec[i][0] )
            jsonMap.put("title", rec[i][1] )
            jsonMap.put("is_completed", rec[i][2] )
            jsonMap.put("requestToken", rec[i][3] )
            jsonMap.put("userId", rec[i][4] )
            jsonMap.put("SharedDays", rec[i][5] )
            jsonMap.put("sharedOnDate", rec[i][6] )
            array.putAt( i, jsonMap )
        }
        booksobject.put("books", array)
        render booksobject as JSON
    }

    def getCategories(){
        def category = Category.findAll()
        render category as JSON
    }

    def getUserDetailByIdOrToken(){
        if(params.token){
         def user  = Book.getUserIDByToken(params.token)
            render user as JSON
        }else if(params.userId){
            def user  = UserTable.findById(params.userId)
            render user as JSON
        }
    }


    def sendPushNotification(String gcm, String requestType, String details){
        bookHelperService.prepareAndroidNotification(gcm, requestType, details)
    }




    def filterBook(){


        println 'filterBook******************'
        def book;
        HashMap jsonMap = new HashMap()


        try{
            def offset = 0;
            if (params.offset){
                offset = Integer.parseInt( params.offset  )* 10;
            }

            if( params.shared && params.category){
                Category category = Category.findByName(params.category)
                book = Book.findAllByCategoryAndIsCompletedAndIsShared(category, false, true, [max: 10, offset: offset]);

            }

            if( params.onsell && params.category){
                Category category = Category.findByName(params.category)
                book = Book.findAllByCategoryAndIsCompletedAndIsOnSell(category, false, true, [max: 10, offset: offset]);

            }


            if( params.donated && params.category){
                Category category = Category.findByName(params.category)
                book = Book.findAllByCategoryAndIsCompletedAndIsDonated(category, false, true, [max: 10, offset: offset]);
            }

            if( params.shared ){
                book = Book.findAllByIsCompletedAndIsShared(false, true, [max: 10, offset: offset]);
            }else if ( params.onsell ){
                book = Book.findAllByIsCompletedAndIsOnSell(false, true, [max: 10, offset: offset]);
            }else if( params.donated ){
                book = Book.findAllByIsCompletedAndIsDonated(false, true, [max: 10, offset: offset]);
            }

            println 'books++++'+book

            jsonMap = bookHelperService.getBookHasMap(book)
            render jsonMap as JSON

        }catch (Exception e){
            println "error occured: " + e.getMessage()
        }

    }

    @Transactional
    def removeUserBook(){
        JSONObject obj = new JSONObject();
        JSONObject responseObj = new JSONObject();

        try{

            def book = Book.findById(params.bookId)

            println 'book'+book

            PickupLocation.findAll().each {it.delete(flush:true, failOnError:true)}
            Request.findAll().each {it.delete(flush:true, failOnError:true)}
            Tags.findAllByBook(book).each {it.delete(flush:true, failOnError:true)}

            if(book.delete(flush: true, failOnError: true)){
            }

                responseObj.put("message", "book Deleted");
                obj.put("success", responseObj)
                render obj as JSON


        }catch (Exception e){
            responseObj.put("message", "failed");
            responseObj.put("exception", e.getMessage());
            obj.put("success", responseObj)
            render obj as JSON
        }

    }

    //http://localhost:8080/bookApp/book/addCustomTag?bookId=8&tags=kdk&pickupId=
    @Transactional
    def addCustomTag(){

        JSONObject obj = new JSONObject();
        JSONObject responseObj = new JSONObject();

        try {

            Tags tags = new Tags(params);

            tags.location = PickupLocation.findById(params.pickupId)
            tags.book = Book.findById(params.bookId)

            if(tags.save( flush:true, failOnError: true )){

                responseObj.put("message", "Tag Added");
                obj.put("success", responseObj)
                render obj as JSON
            }

        }catch (Exception e){
            e.printStackTrace()
            responseObj.put("message", "failed to Add Tag");
            responseObj.put("exception", e.getMessage());
            obj.put("error", responseObj)
            render obj as JSON
        }
    }

    def retriveBookTag(){

        JSONArray array = new JSONArray();
        def book = Book.findById( params.bookId );

//        def tags = Tags.findAllByBook( book );
//        def books = [];
//        tags.each {
//            books.push( Book.findById( it.bookId ) )
//        }
        def tags = Tags.findAllByBook(book)

        def tagList = [];
        tags.each {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("tags", it.tags)
            tagList.push(jsonObject)
        }
        render tagList as JSON
    }

    def searchByTag(){

        JSONObject booksobject = new JSONObject()
        JSONArray array = new JSONArray();

        def books = Tags.createCriteria()
        def results = books.list {
            like("tags", params.tags+"%")

        }


        for(int i=0; i < results.size(); i++ ){

            Book bookInstance = Book.findById(results.get(i).book.id);
            println '---'+results.get(i).book.title
            if(bookInstance != null){
                JSONObject object = bookHelperService.getBookAsJson(bookInstance)
                array.putAt(i,object)
            }
        }

            booksobject.put("books", array)
            render booksobject  as JSON
    }

    def getCityTags(){
        def locat  = PickupLocation.findAllByCity( params.city )
        println '***'+locat
//        def tags =  Tags.findAllByLocation(locat);

        def tags = [];
        locat.each {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("tag", Tags.findByLocation(it).tags )
                    tags.push( jsonObject )
        }
        render tags as JSON

    }

    def getBookListByCity() {

        HashMap jsonMap = new HashMap()
        def bookList = [];
        if(params.offset){
            def offset = Integer.parseInt( params.offset  )* 10;
            def books = PickupLocation.findAllByCity(params.city ,[ max: 10, offset: offset])
            println "----"+ books
            def book;
            books.each {
                book = Book.findById(it.book.id)
                bookList.push(book)
            }

        }else{
            def books = PickupLocation.findAllByCity(params.city)
            println "----"+ books
            def book;
            books.each {
                book = Book.findById(it.book.id)
                bookList.push(book)
            }
        }
        jsonMap = bookHelperService.getBookHasMap(bookList)
        render jsonMap as JSON

    }
}
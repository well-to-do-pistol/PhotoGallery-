最近上传到flickr图片的客户端

1. Retrofit(自动帮你在后台执行网络请求)天生遵循多线程规则:后台和主线程:

   1. Retrofit:将responsebody转换为string对象(用Gson转换器),
      用Retrofit对象create
      api类,用api类执行\@get函数得到call对象,call.enqueue执行网络请求.
      (这里有一个技巧: 创建一个MutableLiveData\<String\>,
      在response成功时设置livedata的value,
      然后在函数的最后返回livedata; fragment得到livedata,
      直接.observe).

   2. string(xml)变json: 定义模型类(data类,kotlin的优点);
      再创建一连串的类的属性对应json的嵌套键值对中的键的名字(用\@SerializedName来标识映射关系).

      a.  指定call的泛型为拥有属性是最外层键的名字的类,
          photos-\>PhotoResponse, PhotoResponse的属性是photo.

      b.  指定返回的livedata的泛型(图片类列表),
          用类=body,或者等于类.属性一步一步剖开嵌套得到数据.

   3. 使用viewmodel防止旋转无限请求(不过一打开应用马上退出,
      网络请求也会继续(bug), 解决方法是在仓库类定义call变量,
      定义取消函数(call.cancel), 在viewmodel的oncleared函数中调用).

   4. 数据源是图片列表, 用列表创建adapter, holder定义绑定函数,
      adapter调用, 将创建adapter赋给recyclerview.

      a.  挑战1: 自定义Gson反序列化器(为了去掉无用的最外层):

          i.  指定返回Response取代Call(可能为了分页).
          
          ii. 自定义JsonDeserializer类, 重写deserialize,
              解析JsonElement和JsonObject, 拿到数据,
              新建类放进类的属性然后返回类.

      b.  挑战2:使用Jetpack的paging分页库和kotlinx-coroutines协程库,
          实现无限请求:(按page分, 每页有100数据)

          i.  只要创建仓库类的时候注入CoroutineScope(viewModelScope),
              就能在协程里调用挂起suspend函数.
          
          ii. 原来是LiveData放数组, 现在是LiveData放PagingData(流):
          
              1.  自定义PagingSource, 重写load函数,
                  里面用page(一开始是1)用仓库类请求数据,
                  把数据放进结果集里, 并且定义前一页为page-1,
                  后一页为page+1.
          
              2.  viewmodel里livedata的转换,
                  将值为string的livedata转换:
                  pager传入size(100)和lambda然后取出livedata(用cachedIn(viewModelScope)缓存).
          
              3.  然后fragment观察这个livedata(内容是pagingdata),
                  用viewLifecycleOwner.lifecycleScope.launch启动协程,
                  协程里adapter.submitdata把pagingdata提交.
          
              4.  adapter改为PagingDataAdapter,
                  在绑定Holder函数用getItem(position)拿到数据,
                  以前是从列表里拿.

      c.  挑战3:动态调整网格列:

          1.  在onViewCreated中添加recyclerview的viewTreeObserver,
              里面定义列为120dp,
              用recyeclerview的宽度除以120得到网格数,
              设置在GridLayoutManager, 然后移除观察者.

2. 主后台线程通信:(为什么要通信: 后台不能更新ui, 主不能耗时)

   1. 再次请求, 解析responsebody字节流得到Bitmap.

   2. 用recyclerview, adapter, bindviewhodler实现按需下载,
      创建一个专用后台线程(HandlerThread)用来下载图片,
      需要传入泛型来判断需要更新的UI.

      a.  这个线程继承LifecycleObserver,
          用注解添加启动和销毁函数(start(), looper, quit()).

      b.  明确传入Holder后, 在fragment保存, 创建实例, 然后添加观察,
          lifecycle.addObserver(), onDestroy里移去observer.

   3. 通信, 主后台都有收件箱(消息队列), 由线程和looper组成,
      它们可以互相放消息到各自信箱, 也可放进自己信箱.
      HandlerThread和Looper对应, Handler和Message对应,
      线程上创建的Handler会自动和它的Looper相关联:

      a.  使用Handler(message handler)往对方消息队列放消息, 它创建,
          发布和处理Message.

      b.  ConcurrentHashMap\<T,String\>管理对应关系,
          ConcurrentHashMap\<String,Boolean\>管理是否预加载.

      c.  线程里新建handler, 获得和发送消息,
          在线程里的onLooperPrepared实现Handler,
          调用获取图片函数(只会传holder, url通过holder获取).
          内部类(Handler)拥有外部类引用,
          不能让它生命周期比外部类长(添加到主线程的Looper).

   4. 主线程中创建的Handler传递给后台线程,
      并且另一参数是lambda(更新ui).

      a.  用(adapter膨胀的)view创建holder,
          用View::setImageDrawable绘制(bitmap变drawable).

      b.  本来可以发送消息到主线程looper, 不过要另一个Handler,
          方便起见, 在后台线程里的请求bitmap函数里, 在请求到bitmap后,
          用主线程传来的Handler.post(Runnable{})来发布Message(相当于把Runnable放进主线程队列里).
          然后现在移除concurrentHashMap的holder键值对,
          再用lambda更新UI.

      c.  为了防止fragment视图销毁, 后台线程挂起,
          但仍发送图片给Holder:

          i.  删除之前后台线程LifecycleObserver的继承,
              在后台线程里面重新创建一个LifecycleObserver{里面添加启动和销毁函数}.
          
          ii. 再创建一个LifecycleObserver{里面添加主线程handler的removeMessage函数和map.clear}.
          
          iii. 在onCreate和onCreateView分别添加两个Observer(记得移除).
          
          iv. 为什么保留fragment:因为后台线程和fragment周期相同(在设备旋转如果实例不删除就不用再重新创建线程),
              正确的方法应该放到仓库里并采用viewmodel(p432).

   5. 挑战

      a.  挑战1,2(p454)尚待解决:

          i.  ThumbnailDownloader继承Observer\<Lifecycle.Event\>,
              将原来的fragment和view观察者全删了,
              将view观察者的函数提取出来形成clearQueue,
              重写onChange(当值为启动和结束时分别调用start()looper和quit()quitSafely())
          
          ii. fragment里加一个MutableLiveData\<Lifecycle.Event\>对象,
              但凡涉及添加fragment和view观察者都删了,
              onCreate后面加2句thumbnailDownloader.observeLifecycleEvents(lifecycleEvents),
              lifecycleEvents.value = Lifecycle.Event.ON_CREATE,
              OnDestroyView加thumbnailDownloader.clearQueue(),
              OnDestroy加lifecycleEvents.value =
              Lifecycle.Event.ON_DESTROY
          
          iii. 实现了:HandlerThread和fragment视图周期同步;
          
          iv. 视图的生命周期（viewLifecycleOwner）与Fragment自身的生命周期不完全一致。Fragment的视图在onCreateView()中创建，在onDestroyView()中销毁，因此视图的生命周期比Fragment的生命周期短。利用getViewLifecycleOwnerLiveData()方法，可以确保只在视图存在时才进行操作，从而防止在视图不存在时尝试更新UI，这可能会导致应用崩溃
          
          v.  难搞啊, 实现不了

      b.  挑战3: 缓存

          i.  ThumbnailDownloader建对象LruCache\<String, Bitmap\>,
              init里用1/8内存初始化LruCache;
              handleRequest的时候用传入的holder拿到了url,
              用url在LruCache找bitmap,
              找到了直接按原来配置一下onThumbnailDownloaded(target,
              bitmap), 找不到用flickrFetchr.fetchPhoto(url)请求图片,
              用put把图片放缓存然后同上.

      c.  挑战4: 预加载:(从holder开始):

          i.  利用holder的position(正常的利用数组的getsize获取position,
              但这里只有adapter.submitdata)+4,+25,+50来调用后台线程里面的函数,
              四个线程分别通过自己的HandlerThread和Handler进行图片请求:
              先判断用url判断lrucache有没有bitmap和请求map是否为true,
              都没有则obtainMessage和sendtoTarget.
          
          ii. 然后在onLooperPrepared管理各自的队列(用常量判断),
              然后去请求图片(只有缓存里有图片且预加载map有url才会去删除预加载map(预加载map可能会出现过大的问题))

3. 搜索和sharedpreferences:

   1. 如果添加的参数相同, 可自定义Interceptor重写方法,
      取出原始网络请求, 取出原始URL, 用builder添加需要的参数,
      创建新的网络请求, 把拦截器添加到Retrofit参数配置里.

   2. 原来用call, 现在全部用Response得到数据(搜索).
      搜索框用actionview组件.

   3. PagingSource会利用Retrofit设置item, 将item加进缓存.
      LiveData是通过pager+pagingSource-\>livedata拿到的,
      实质还是LiveData\<PagingData\<GalleryItem\>\>,
      仓库类保存在viewmodel里.

   4. fragment菜单栏函数里监听actionview,
      提交后执行viewmodel里的图片搜索(拿图片是通过(Retrofit)解析responsebody字节流得到Bitmap)(用后台线程是因为用Retrofit要管理很多call,holder,fragment,
      而且图片太多, 所以自己创建后台线程, 重复利用Retrofit).

   5. sharedpreferences(init的时候就拿出来, 搜索图片的时候保存):

      a.  viewmodel要继承AndroidViewModel(Application)来引用上下文来得到sharedpreferences.

   6. 挑战:优化:先不管(p469).

4. work-runtime库(间隔一定时间执行任务):

   1. 新建Worker类, 重写doWork不能安排耗时任务;

   2. 先添加sharedpreference保存id函数, 仓库类暴露Retrofit call,
      doWork拿到保存的字串和ID,
      调用获取图片(这里就不用page了,因为只需要第一张图片).
      比较列表第一张ID和保存的ID.

   3. 通知:

      a.  自定义Application类, 创建NotificationChannel,
          manifest的application要更新为自定义的类.

      b.  worker里调用activity的newIntent放进pendingIntent,
          用notificationManager创建notification(图标,视图,pendingIntent,notificationChannel),
          用.notify.

   4. 启停轮询任务:

      a.  在sharedPreference保存是否轮询值.

      b.  菜单项如果拿到轮询值是true显示停止否则显示启动.

      c.  点击(选择菜单)时获取轮询值, 如果true用workManager取消任务,
          保存轮询fasle值; 否则用workManager启动服务.

5. 为了在应用启动时不通知用户(因为worker无法知道activity是否运行)(原理:
   轮询获取新图片发送broadcast intent, 创建两个broadcast
   receiver:1.manifest登记(只要收到消息就通知);2.动态登记(阻止第一个)):

   1. 在worker里sendBroadcast(Intent). a.standalone receiver;
      b.dynamic receiver.

   2. standalone receiver:

      a.  创建BroadcastReceiver自定义类,
          在manifest添加这个receiver(登记),
          并且定义intent-filter只接受带特定常量的intent.

      b.  mainifest用protection level签名定义自己的定制权限,
          \<permission\>,\<uses-permission\>.

      c.  在receiver里添加permission,
          对外不开放.(两个receiver都设置了签名权限).

      d.  worker里sendBroadcast(Intent)加上权限(常量).

   3. dynamic receiver:

      a.  为了创建生命周期和fragment一样的receiver, 自定义Fragment类,
          类里自定义BroadcastReceiver变量,
          在onStart和onStop登记和取消登记receiver.(p493:在onCreate和onDestroy使用requireActivity会得到不同的值(因为它在调用onCreate时,
          activity也在这个阶段, 初始化可能未全部完成)).

      b.  主fragment继承自定义fragment.

      c.  自定义fragment里的receiver重写onReceive接收后将结果码改为canceled.

   4. 在worker里设置intent(放了Notification)再sendOrderedBroadcast,
      结果码会被设置为ok.

   5. standalone receiver里收到后先判断结果码不是ok直接return,
      拿出Intent的Notification, 再用notificationManager发送通知.
      manifest的intent filter还要设置priority为-999.

      a.  注意: 安卓不再发送隐式广播(就是没有指定receiver,
          只给了一个Action)给manifest文件里声明的broadcast reveiver.

      b.  但是我们使用了signature-level权限发送的广播, 所以不受限,
          还有另一部分内部广播也是不受限的, 因为别的应用无法发送,
          这就不会到至类似按下相机快门,
          所有应用都来备份相片.(不会带来性能问题).

6. 网页浏览

   1. 浏览器

      a.  需要在模型类进行拼接得到图片的网址Uri.

      b.  监听holder,
          点击启动ACTION_VIEW隐式intent(用adapter传来的模型对象的网址Uri).
          adapter的绑定函数添加holder的对应函数就行了.

   2. WebView

      a.  新建activity(要在manifest声明)和fragment,
          传数据给activity:intent里的data;
          activity传数据给fragment:intent里的arguments.

      b.  fragment简单配置webview. (webview也可以调用应用里的函数).

      c.  webChromeClient(这不是谷歌浏览器的意思)设置进度条和supportActionBar?.subtitle标题.

      d.  在manifest的activity加上一句(因键盘开关, 屏幕方向改变,
          屏幕大小改变)让activity自己处理设备配置更改(VideoView也是类似)(有一定风险)(viewmodel,
          onStateInstanceState,
          创建横向布局可能会更好).(警惕javascript接口暴露,
          虽然只有注解的函数会暴露).

      e.  重写PhotoPageActivity里的onBackPressed,
          检查webView.canGoBack(), 如果可以回退就小回退,
          不可以就直接大回退.

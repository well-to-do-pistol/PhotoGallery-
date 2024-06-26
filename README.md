**PhotoGallery(Kotlin):**

配置(零):

> 1\. 主线程用Handler发holder和message给后台线程,
> 后台线程从Looper拿到图片后, 用Runnable(new
> Runnable{执行图片UI更新的函数})发送holder和bitmap给主线程,
> 主线程更新图片
>
> 2\. 用原生搜索框,
> sharedpreferences实现轻量级搜索字符串存储(26章)和最新图片ID存储(p474)
>
> 3\. 四大组件broadcast receiver,
> 可以自建一个抽象fragment里面登记receiver(需要匿名内部类BroadcastReceiver弄一个对象,
> 再注册), 再用fragment继承这个抽象
>
> 4\.
> 先自定义receiver在visiblefragment(它会接收把结果码设为取消(不让别人接收))(被PhotoGalleryFragment继承),
> 再创建通知receiver(优先级为-999, 用来发送通知);
> PollWork在轮询的时候顺便发通知, fragment优先级是-999(别人不收它才收)
>
> 5\. webView:先点击holder带着uri启动activity再切换fragment

M:

1.  HTTP与后台任务

```{=html}

```

1. 新建activity_photo_gallery(Framelayout)(容器),
   在PhotoGalleryActivity的OnCreate判空后加fragment用beginTransaction

2. 加fragment_photo_gallery.xml(RecyclerView).
   建PhotoGalleryFragment(onCreateView初始化RecyclerView(网格)),
   伴随对象加构造方法

3. app.gradle加Retrofit库依赖(p402). 加api包, 包里建FlickrApi(接口,
   \@Get(get请求,"/"设为相对路径)注释fetchContents()返回Call\<String\>)

4. PhotoGalleryFragment的onCreate用网址build
   Retrofit再通过它创建api接口

5. 网络请求

   a.  app.gradle加scalars converter库依赖进行数据类型转换,
       在PhotoGalleryFragment里onCreate的build
       Retrofit的时候加一句addConverterFactory,
       并在最后用接口.fetchContents()返回Call\<String\>

   b.  onCreate后面用call.enqueue异步执行网络请求

   c.  在manifest添加网络权限

6. 使用仓库模式联网

   a.  加FlickrFetchr.kt(里面加个api对象, init里build
       Retrofit再通过它创建api接口)-\>fragment相应代码删除;
       FlickrFetchr加个fetchContents函数(加MutableLiveData对象和Call\<String\>对象(用api.fetchContents得到),
       再用call.enqueue异步请求-\>成功后设liveData值为response.body(),
       返回liveData)

   b.  fragment的onCreate所有语句清除,
       加入liveData对象用FlickrFetchr().fetchContents()得到,
       并观察liveData

7. 从Flickr获取Json数据

   a.  改变FlickrApi的get路径为相应字符串(p412), 函数名改一下.
       将FlickrFetchr build Retrofit的路径从'www'改为'api', 更改函数名,
       fragment函数名也改

   b.  加GalleryItem(data)类(title, id, url),
       app.gradle添加两句Gson依赖(p415)

   c.  在data类的url用注释@声明序列化的名字

   d.  加PhotoResponse(次外层)类(加List\<GalleryItem\>对象并用注释@声明序列化的名字(photo)).
       加FlickrResponse(最外层)类加PhotoResponse对象

   e.  更改FlickrApi的fetchPhotos返回\<String\>改为\<FlickrResponse\>

   f.  更改FlickrFetchr的init的build
       Retrofit的ScalarsConverterFactory为GsonConverterFactory;
       先把所有的String改成FlickrResponse, 再删除enqueue成功里的语句,
       加(将response.body()转换为FlickrResponse,
       调用FlickrResponse.photos生成photoResponse,
       调用photoResponse.galleryItems生成List\<GalleryItem\>,
       过滤一下将liveData的值设为List\<GalleryItem\>)

   g.  更新fragment中onCreate的liveData对象的String, 观察的名字改一下

8. 应对设备配置改变

   a.  app.gradle加lifecycle-extensions依赖(p419).
       加PhotoGalleryViewModel(加liveData对象,
       init初始化liveData用FlickrFetchr().fetchPhotos()得到)

   b.  更改fragment, 添加viewmodel对象, 删除liveData的赋值和观察,
       初始化viewModel, onViewCreaetd观察viewModel的liveData

9. 在RecyclerView显示结果

   a.  fragment加PhotoHolder类,
       加bindTitle方法(传入CharSequence返回unit)等于view::setText(调用view的setText())(lambda表达式和方法引用(将函数作为参数传递))

   b.  fragment加PhotoAdapter(传入List\<GalleryItem\>)(新建textView(创建holder时传入),
       绑定时调用holder的绑定方法传入galleryItem单项的title)

   c.  fragment的onViewCreated里的viewModel的liveData的观察里设置photoRecyclerView的适配器为adapter(liveData的值(数组))

10. 三个挑战

    a.  自定义Gson反序列化器

        i.  FlickrApi的Call中的\<FlickrResponse\>改为\<PhotoResponse\>
        
        ii. 将FlickrFetchr里init的build
            Retrofit中的addConterter中create加个gson(.registerTypeAdapter(PhotoResponse::class.java,
            PhotoDeserializer())得到gson(自定义序列化器))
        
        iii. 将FlickrFetchr所有\<FlickrResponse\>改为\<PhotoResponse\>;
             函数FlickrFetchr的onResponse成功后liveData的设值改为post

    b.  分页(之前能传数组, 现在PagingDataAdapter只能传单项)

        i.  加入paging 和kotlinx-coroutines依赖
        
        ii. FlickrFetchr改为传入CoroutineScope不再返回liveData直接返回包含原始对象的数组,
            里面fetchPhotos方法改为suspend和传入int(page):
            用call.enqueue得到body来post和返回liveData的方法改为-\>直接判isSuccessful拿到body的数组直接返回
        
        iii. fragment的onViewCreated的观察viewModel的liveData:
             原来是观察值传数组到adapter然后设置,
             现在改为先初始化空的设置,
             然后viewLifecycleOwner.lifecycleScope.launch弄了一个协程,
             观察值传单项不传数组了到adapter(submitData)然后设置
        
        iv. adapter改为传单项不传数组, 改为继承PagingDataAdapter,
            后面bindHolder改为getItem(position)
        
        v.  PhotoGalleryViewModel里初始化对象改为FlickrFetchr(viewModelScope)(
            CoroutineScope); liveData的初始化改为

> val galleryItemLiveData: LiveData\<PagingData\<GalleryItem\>\> =
> Pager(PagingConfig(pageSize = 100)) {
>
> FlickrPagingSource(flickrFetchr)
>
> }.liveData
>
> .cachedIn(viewModelScope)

vi. api包中的api中的函数改为suspend fun fetchPhotos(@Query(\"page\")
    page: Int): Response\<PhotoResponse\>

vii. api包加FlickrPagingSource类, 继承PagingSource\<Int,
     GalleryItem\>()传入FlickrFetchr, 重写两方法, load方法里,
     拿到params.key的int(page)后传入FlickrFetchr的fetchPhoto,
     用得到的bitmap配置等等

```{=html}

```

11. a.  动态调整网格列

        i.  一开始是3个,
            通过resources.getDimension(R.dimen.column_width)(values新建的resource记录dimen为120dp)计算后layoutManager.spanCount
            = numberOfColumns设值数量然后移除ViewTreeObserver

```{=html}

```

2.  Looper, Handler, HandlerThread

```{=html}

```

1. 加list_item_gallery.xm(加个Image).
   fragment的holder的bind改为setImageDrawable;
   adapter的创建holder为膨胀Image传入构建holder然后返回;
   adapter的绑定函数中holder绑text改为初始化drawable(用图片)后绑图片---看到大头照了

2. 准备下载数据

   a.  FlickrApi加个@GET函数fetchUrlBytes(传入String(@Url注释)返回Call\<okhttp3.ResponseBody\>)

   b.  FlickrFetchr里加个@WorkerThread函数fetchPhoto(传入String返回Bitmap),
       先用execute调用api的fetchUrlBytes拿到Call(Response),
       从response.body中解码得到bitmap返回

3. 创建后台线程

   a.  加ThumbnailDownloader类, 传入\<in 泛型\>返回HandlerThread,
       加quit的Boolean判断, 重写quit将判断变为true,
       加queueThumbnail(传入T和String)

   b.  创建生命周期感知线程

       i.  fragment的onCreate里加一句retainInstance=true保留fragment应对设备旋转(只能,
           不能应付内存清除, 并且最好不要保留)
       
       ii. ThumbnailDownloader继承LifecycleObserver加两个用@OnlifecycleEvent注释的启动和结束函数
       
       iii. fragment加个ThumbnailDownloader\<PhotoHolder\>对象,
            onCreate初始化, 用lifecycle观察它; onDestroy移除观察

   c.  启停HandlerThread

       i.  ThumbnailDownloader里启动函数加个start()和looper,
           结束函数加个quit()
       
       ii. fragment中adapter中的bind调用ThumbnailDownloader的queueThumbnail函数(传入holder和单项的url)

4. Message与message handler(前后台线程的通信,
   消息队列)(主线程用Handler发holder和message给后台线程,
   后台线程从Looper拿到图片后, 用Runnable发送holder和bitmap给主线程,
   主线程更新图片 )

   a.  ThumbnailDownloader添加DOWNLOAD常量=0, 加3对象(Handler,
       ConcurrentHashMap\<T, String\>, FlickrFetchr())

   b.  ThumbnailDownloader在queueThumbnail里将传入的holder(键)和String(值)加入ConcurrentHashMap,
       Handler调用obtainMessage传入DOWNLOAD常量和holder再.sendToTarget()(线程是能发消息的)

   c.  ThumbnailDownloader重写onLooperPrepared(用两个注释(一个忽略报错一个Lint内存泄漏)),
       初始化Handler为匿名内部类重写handleMessage(判断msg.what等于DOWNLOAD常量,
       是则将msg.obj转holder, 调用handleRequest传入holder)

   d.  ThumbnailDownloader创建上一点要调用的handleRequest(传入T(holder)),
       里面从ConcurrentHashMap根据传入holder拿到url,
       用flickrFetchr.fetchPhoto(url)(这个函数已经注释了要后台线程)拿到bitmap

   e.  ThumbnailDownloader类修改要传入(Handler和(onThumbnailDownloaded)一个函数(传入T(holder)和Bitmap))

   f.  fragment里onCreate弄个Hander()变量,
       删除之前的ThumbnailDownloader初始化语句改为传入Handler和新建匿名函数{传入holder和bitmp,
       里面BitmapDrawbale(传入resources和bitmap)拿到drawbale,
       在用holder调用bindDrawable传入drawable}

   g.  ThumbnailDownloader的handleRequest里最后用Handler.post(传入初始化一个Runnable{判空,
       然后从ConcurrentHashMap移除该holder键,
       调用onThumbnailDownloaded(传入holder和bitmap)})

5. 观察视图的生命周期

   a.  ThumbnailDownloader不继承LifecycleObserver了,
       删除@OnLifecycleEvent注释的启动和结束函数,
       加一个LifecycleObserver对象用匿名内部类LifecycleObserver{将删除启动和结束函数添加到这里来}

   b.  ThumbnailDownloader又加一个LifecycleObserver对象用匿名内部类LifecycleObserver{加@OnLifecycleEvent
       (DESTROY)注释的clearQueue函数(Handler移除Messages输入DOWNLOAD常量,
       直接ConcurrentHashMap.clear()清空map)},
       这两个一个是fragment观察者一个是view观察者

   c.  fragment的onCreate的最后一句加观察者加个.调用fragment观察者;
       onCreateVIew用viewLifecycleOwner加观察者为ThumbnailDownloader的view观察者

   d.  fragment的onDestroyView里viewLifecycleOwner移除view观察者,
       onDestroy移除fragment观察者---有真图啦

6. 挑战(3个)

   a.  1_观察视图LifecycleOwner的LiveData:
       Fragment.getViewLifecycleOwnerLiveData() 返回的
       LiveData\<LifecycleOwner\> 来观察 Fragment 的
       viewLifecycleOwner; 2_优化ThumbnailDownloader:
       使它成为生命周期感知类, Fragment.onDestroyView()
       函数被调用时清除下载任务队列

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

   b.  缓存(拿图片不用每次都请求,
       可以看LruCache有没有直接在那拿)(android.util.LruCache)

       i.  ThumbnailDownloader建对象LruCache\<String, Bitmap\>,
           init里用1/8内存初始化LruCache;
           handleRequest的时候用传入的holder拿到了url,
           用url在LruCache找bitmap,
           找到了直接按原来配置一下onThumbnailDownloaded(target,
           bitmap), 找不到用flickrFetchr.fetchPhoto(url)请求图片,
           用put把图片放缓存然后同上

   c.  预加载(小预加载: 这是没改PagingDataAdapter的,
       要分页的话预加载就难咯)

       i.  ThumbnailDownloader
       
           1.  加个PRELOAD常量和(预加载map)ConcurrentHashMap\<String,
               Boolean\>()
       
           2.  重载handleRequest函数(加到缓存(不更新视图)即为预加载)加个输入isPreload,
               从缓存拿图片, 没有就下载并放到缓存,
               判断isPreload如果是则onThumbnailDownloaded(target,
               bitmap)
       
           3.  加个preloadThumbnail函数传入url判断缓存里为空而且preloadRequestSet.putIfAbsent(url,
               true) == null,
               则requestHandler.obtainMessage(MESSAGE_PRELOAD,
               url).sendToTarget()发送消息
       
           4.  (接收消息后调用)加processDownload(传入T(holder)用holder从requestMap拿url,
               调用handleRequest(isPreload是false));
               加processPreload(传入url)函数,
               它调用handleRequest函数(传入url调用handleRequest(isPreload是true))
       
           5.  修改onLooperPrepared的handleMessage判断msg.what是哪个常量,
               DOWNLOAD常量msg.obj拿到T调用processDownload,
               PRELOAD常量msg.obj拿到String(url)调用
       
       ii. fragment
       
           1.  添加两个预加载函数,
               都是传入数组(取一部分预加载或者全部预加载)thumbnailDownloader.preloadThumbnail
       
           2.  onViewCreated中viewModel的liveData监听,
               添加滚动函数传入数组或直接传调用预加载函数

```{=html}
<!-- -->
```

3.  搜索

```{=html}

```

1. (拦截器)在api包新建PhotoInterceptor类, 继承Interceptor,
   返回chain.proceed(newRequest(由chain.request()和HttpUrl(一堆参数包括:密钥,
   格式, 返回类型, extras, safesearch)))

2. (添加拦截器)FlickrFetchr的init加个OkHttpClient(用拦截器), z在build
   Retrofit的时候加.client(client)

3. (添加搜索函数)FlickrAPi里的@GET里原来的路径不需要了(现在有拦截器帮忙配置了),
   添加@GET的searchPhotos输入String(用@Query注释是text)返回Call,
   路径点明是flickr.photos.search

4. (向FlickrFetchr添加搜索函数)原来的fetchPhotos改成fetchPhotoMetadata(输入Call)返回liveData\<数组\>,
   里面的从api拿Call的语句删掉;
   加fetchPhotos和searchPhotos(输入String)函数(都是返回liveData\<数组\>),
   返回(调用fetchPhotoMetadata(分别输入api.fetchPhotos()和api.searchPhotos(String)))

5. (发起搜素)PhotoGalleryViewModel里的init拿的liveData改为用searchPhotos拿(FlickrFetchr)---可以搜素了

6. res/menu建fragment_photo_gallery.xml的menu里包含search和clear两个item(配置一下).
   strings.xml添加搜素字符串.
   fragment的onCreate里保留fragment语句后添加个setHasOptionsMenu(true)接收菜单回调函数;
   重写onCreateOptionsMenu(膨胀菜单xml)

7. 响应用户搜素

   a.  PhotoGalleryViewModel里加2个对象(FlickrFetchr()和MutableLiveData\<String\>),
       init里设liveData\<String\>为'行星',
       真正liveData的赋值改为Transformations.switchMap(liveData\<String\>用里面的值来进行searchPhotos);
       再加个函数fetchPhotos(输入query默认为String="")(liveData\<String\>值设为query)

   b.  fragment的onCreateOptionsMenu里加2对象(MenuItem(由菜单的搜素id膨胀),
       MenuItem.actionView),
       视图.apply(设置queryText监听-\>匿名内部类(重写2函数,
       在submit那个拿queryText通过viewModel.fetchPhotos搜素))---可以用菜单栏输入搜素了

8. 使用sharedpreferences实现轻量级数据存储

   a.  加QueryPreferences(object, 加个SEARCH常量,
       加两函数getStoredQuery(传入context返回String)和setStoredQuery(传入context和String返回String)),
       第二个函数:用PreferenceManager配置然后putString(SEARCH常量,
       搜素String)保存,
       第一个函数:-用PreferenceManager.getDefaultSharedPreferences和.getString(SEARCH常量)拿到搜素String

   b.  PhotoGalleryViewModel改为传入(Application)和继承AndroidViewModel(Application),
       init里从="行星"改为QueryPreferences.getStoredQuery(Application);
       fetchPhotos里面也加个QueryPreferences.setStoredQuery(Application,
       String)

   c.  (清除查询值)fragment里重写onOptionsItemSelected(当id时clear时调用viewModel.fetchPhotos("")(相当于也保存了一个空字符串在sharedpreferences),
       else super)

   d.  (遇空查询就调用不用参数的fetchPhotos)在PhotoGalleryViewModel里真正liveData里switchMap时判空当空值时调用flickrFetchr.fetchPhotos()而不用搜索

9. 点击搜索按钮时, searchView显示已保存的查询字符串

   a.  PhotoGalleryViewModel里加个(searchTerm)String对象(get()=liveData\<String\>的值)

   b.  (预设搜索文本框)fragment里onCreateOptionsMenu里searchView.apply里设搜索文字监听下面加搜索点击监听,
       searchView.setQuery(viewModel.searchTerm, false)

10. 加Android KTX依赖, 使用java api时能直接使用kotlin特性,
    QueryPreferences的setStoredQuery函数里edit().puString().apply()直接改成edit{putString()}(Lambda
    with receiver用{}然后在里面访问被调用对象的成员)

11. 挑战

    a.  点击搜索隐藏软键盘和搜索框

        i.  s

    b.  点击搜索显示加载动画

        i.  逻辑问题

```{=html}

```

4.  WorkManager(后台轮询访问Flicker看看有没有新图片)

```{=html}

```

1. app.gradle添加WorkManager库依赖,
   新建PollWorker类(传入context和WorkerParameters继承Worker(context,
   WorkerParameters)), 重写doWork返回Result.success()

2. 调度工作

   a.  (调度一个WorkRequest)fragment的onCreate里用PollWorker类build
       OneTimeWorkRequest,
       用WorkManager来enqueue(输入OneTimeWorkRequest)

   b.  (设限(只能在wifi下(无计量)运行))fragment里onCreate的bulid
       OneTimeWorkRequest里set个限制,
       在它前面build个无计量限制(NetworkType.UNMETERED)

3. 检查新图片(sharedpreferences实现轻量级存储)

   a.  保存最新图片ID)QueryPreference里加个LRESULT常量,
       加两函数getLasResultId(传入context返回String)和setLasResultId
       (传入context和String返回String)),
       第二个函数:用PreferenceManager配置然后putString(LRESULT常量,
       搜素String)保存,
       第一个函数:-用PreferenceManager.getDefaultSharedPreferences和.getString(LRESULT常量)拿到最新ID的String

   b.  (将返回Call的过程分解并暴露出来)FlickrFetchr里添加fetchPhotosRequest和searchPhotosRequest两个函数(一个无输入一个输入String,
       都是返回Call), 里面分别调用api的fetch和search,
       修改这里的fetch和search为分别调用前两个函数

   c.  (Worker(PollWorker执行网络请求)获取最新图片)PollWorker里doWork里加(先用QueryPreference的函数拿到存储的搜素字符串,
       再同样方式拿到存储最新ID,
       建一个List\<GalleryItem\>对象判搜索字符串空,
       根据判断结果分别使用FlickrFetchr().fetchPhotoRequest和searchPhotoRequest拿到Call后在.executer()再.body()再.photos再.galleryItems)

   d.  (Worker(PollWorker执行网络请求)接下来接着上面一点: 判空,
       然后拿到(List\<GalleryItem\>)数组的第一个ID判断是否和拿出来的最新ID相同,
       相同则表明旧, 不同则表明新(调用QueryPreference的存储函数)

4. 通知用户

   a.  (创建通知渠道)建PhotoGalleryApplication类继承Application,
       加CHANNEL常量, onCreate判断版本如果大于26的话(用CHANNEL常量,
       名字, importance构建NotificationChannel),
       再用NotificationManger通过NotificationChannel构建通知渠道

   b.  strings.xml添加字符串资源

   c.  manifest里application的name设置为PhotoGalleryApplication

   d.  (给activity添加newIntent())activity里伴随对象加个newIntent(传入context)返回自己(Intent)

   e.  (添加Notification)PollWorker里doWork里再判断网络请求到的第一个数组ID是不是最新时,
       如果不相等(最新), 在保存后面加(新建一个activity的intent,
       在生成PendingIntent.getActivity(context,0,intent,0),
       再从context.resources拿资源,
       用NotificationCompat一系列配置后得到Notification,
       用NotificationManagerCompat.notify(0,
       notification))---可以在状态栏看到通知了

5. 服务的用户控制

   a.  QueryPreferences加POLLING常量,
       加两函数isPolling(传入context返回Boolean)和setPolling(传入context和Boolean返回Boolean)),
       第二个函数:用PreferenceManager配置然后putBoolean(POLLING常量,
       isOn)保存,
       第一个函数:-用PreferenceManager.getDefaultSharedPreferences和.getBoolean
       (POLLING常量)拿到是否要轮询

   b.  strings.xml添加轮询字符串资源

   c.  fragment_photo_gallery的menu加个polling的item

   d.  (显示是否轮询item文字)fragment的onCreate的建立限制以及WorkManager.getInstance().enqueue(workRequest)等相关语句删除,
       在onCreateOptionsMenu里用menu.findItem(polling的id)拿到toggleItem,
       QueryPreference用函数拿到存储的isPolling,
       判断isPolling将为title在不同情况赋值, toggleItem.setTitle

   e.  (响应菜单项服务启停点击)PhotoGalleryFragment类里加个POLL_WORK常量,
       onOptionsItemSelected里当id为polling时:
       QueryPreference用函数拿到存储的isPolling.
       如果isPolling为true(true的时候用户选择了菜单项证明想停掉),
       WorkManager来用POLL_WORK常量取消轮询,
       QueryPreference用函数保存isPolling为fasle; 如果isPolling为false
       (false的时候用户选择了菜单项证明想启用), 建立UNMETERED限制,
       再用WorkManager用POLL_WORK启用轮询,
       QueryPreference用函数保存isPolling为true(enqueueUniquePeriodicWork,
       keep), activity.invalidateOptionsMenu 返回true

R:

5.  broadcast intent_四大组件broadcast
    receiver(应用运行时阻止轮询服务发送通知)

```{=html}

```

1. 过滤前台通知

   a.  (发送broadcast
       intent)PollWorker里doWork里notificationManager.notify后加一句context.sendBroadcast(Intent(ACTION_SHOW_NOTIFICATION)),
       伴随对象里设置常量ACTION_SHOW_NOTIFICATION为项目包路径加SHOW_NOTIFICATION

   b.  (创建broadcast
       receiver)新建NotificationReceiver类继承BroadcastReceiver,
       重写onReceive

   c.  在manifest的appliaction里和activity同级加个receiver设置name为NotificationReceiver,
       里面加intent-filter设name为第一点的ACTION_SHOW_NOTIFICATION常量内容---现在旧版已经调用onReceive了,
       新版不行

   d.  使用私有权限限制broadcast

       i.  (添加私有权限)manifest里的manifest...里加\<permission
           android:name="com.bignerdranch.android.photogallery.PRIVATE"
           android:protectionLevel="signature"/\>和\<uses-permission
           android:name="com.bignerdranch.android.photogallery.PRIVATE"\>(定制权限的唯一标识字符串)
       
       ii. manifest的receiver里设置permission(定制权限), exported=false
       
       iii. (发送带有定制权限的broadcast)PollWork里伴随对象加上定制权限的常量,
            然后在doWork的sendBroadcast加上这个常量

   e.  创建并登记动态receiver

       i.  (VisibleFragment自己的receiver)新建VisibleFragment(抽象,
           继承Fragment):
           加个onShowNotification对象由匿名内部类BroadcastReceiver(){重写onReceive},
           onStart里设IntentFilter(PollWorker.
           ACTION_SHOW_NOTIFICATION),
           registerReceiver用上onShowNotification, filter,
           PollWorker的定制权限常量, null---(activity登记receiver);
           onStop里用onShowNotification取消登记
       
       ii. (设置fragment为可见)PhotoGalleryFragment取消继承Fragment改为继承VisibleFragment

   f.  使用有序broadcast收发数据(必须要自己的receiver先收到自己发的broadcast)

       i.  (返回一个简单结果码)VisibleFragment里onShowNotification里的onReceive里加个resultCode=Activity.RESULT_CANCELED
       
       ii. (发送有序broadcast)PollWorker里doWork里删除NotificationManagerCompat以及context.sendBroadcast响应语句,
           改为showBackgroundNotification(0, notificaiton),
           加showBackgroundNotification函数(传入int, Notification):
           Intent(ACTION_SHOW_NOTIFICATION).apply{putExtra(两个常量(这里伴随对象会声明),
           int和Notification)},
           然后context.sendOrderedBroadcast(intent, 定制权限常量)
       
       iii. (发送有序broadcast)sendBroadcast保证broadcast一次一个投递给receiver,
            发送出去后, 结果码会被设置为Activity.RESULT_OK
       
       iv. (发送通知给目标用户, 实现result
           receiver)NotificationReceiver里onReceive里判断结果码是不是Activity.RESULT_OK,
           然后从intent里getExtra拿到int和Notification,
           再用NotificationManagerCompat.notify(requestCode,
           notification)
       
       v.  (修改notification
           receiver的优先级)manifest里设置receiver的intent-filter的priority="-999"(没有接收者它才接收)---现在应用开着时通知不会出现了

2. 有序广播说明: 应用位于前台时, VisibleFragment
   的接收者会首先接收到广播，并取消广播的进一步传递。因此，只有当应用不在前台时，NotificationReceiver
   才会接收到广播并显示通知---文档的开头也有说明

```{=html}

```

6.  网页预览(点击图片打开网页)

```{=html}

```

1. (添加创建图片URL的代码)GalleryItem里加个String(@SerializedName("owner")注释),
   再加个Uri(自定义访问器属性)通过owner和id计算等get到

2. 简单方式: 使用隐式intent

   a.  (通过隐式intent实现网页浏览)PhotoGalleryFragment里的holder继承onClick监听(加一个GalleryItem对象,
       init设置itemView的监听,
       加bindGalleryItem方法(传入GalleryItem)(里面galleryItem=GalleryItem),
       重写OnClick, 新建intent(ACTION_VIEW, galleryItem拿到uri然后strat
       intent))

   b.  (绑定GalleryItem)PhotoGalleryFragment的adapter里的bindHolder里调用holder.bindGalleryItem(galleryItem)

3. 较难的方式: 使用WebView

   a.  (创建网页浏览fragment)新建PhotoPageFragment继承visiblefragment,
       fragment.xml里限制布局一个progressbar一个webview

       i.  加两个对象: uri和webview
       
       ii. onCreate里初始化uri=arguments.getParcelable(ARG_URI)
       
       iii. onCreateView膨胀fragment.xml, 初始化webView
       
       iv. 伴随对象里newInstance(传入uri).apply{用Bundel().putParcelable(ARG_URI,uri)}

   b.  (创建显示网页的activity)新建PhotoPageActivity继承AppCompatActivity

       i.  onCreate用supportFragmentManager找到容器后.beginTransaction加入PhotoPageFragment(传入intent.data)
       
       ii. 伴随对象里newIntent传入context和uri, Intent(context,
           自己::class.java)然后.apply{data=photoPageUri}
       
       iii. 说明这个activity只能被newIntent来启动(带着uri)
       
       iv. 这个activity.xml改为FrameLayout加个容器id

   c.  webView显示

       i.  PhotoGalleryFragment里holder的onClick的隐式intent改为PhotoPageActivity.newIntent(context,
           单项里拿到uri). manifest里application声明activity name=该名
       
       ii. (加载url)PhotoPageFragment里OnCreateView里webView拿到后配置3个语句(最后是loadUrl(uri))

   d.  使用WebChromeClient优化显示

       i.  在PhotoPageFragment里onCreateView里初始化progressbar,并设.max=100(横条加载),
           再在最后设置webView.webChromeClient展开匿名内部类(重写两个方法:
           change方法判断newProgress是否等于100, 是则隐藏progressBar,
           不是则显示, bar.progress=newProgress(设值);
           receiveTitle方法设supportActionBar的subtitle为title)

   e.  处理WebView的设备旋转问题

       i.  (让activity自己处理设备配置更改)manifest的PhotoPageActivity那里设configChanges="keyboardHidden\|orientation\|screenSize"(键盘开关\|屏幕方向\|屏幕大小)

   f.  挑战:使用回退键浏览历史网页

       i.  逻辑问题

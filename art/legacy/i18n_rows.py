from pathlib import Path
import runpy
# zh-CN | zh-HK | zh-TW | en | ja
R = r"""
启动游戏|啟動遊戲|啟動遊戲|Play|ゲーム開始
开始游戏|開始遊戲|開始遊戲|Play|プレイ
资源中心|資源中心|資源中心|Resources|リソース
实例管理|實例管理|實例管理|Instances|インスタンス
当前实例|目前實例|目前實例|Current instance|現在のインスタンス
玩家名|玩家名稱|玩家名稱|Player name|プレイヤー名
内存|記憶體|記憶體|Memory|メモリ
内存分配（MB）|記憶體分配（MB）|記憶體分配（MB）|Memory (MB)|メモリ割り当て（MB）
模组与材质包|模組與材質包|模組與材質包|Mods & Packs|Mod とテクスチャ
模组与资源|模組與資源|模組與資源|Mods & Packs|Mod とリソース
加载器|載入器|載入器|Loader|ローダー
版本下载|版本下載|版本下載|Versions|バージョン
添加本地资源|加入本機資源|加入本機資源|Local files|ローカル追加
打开实例目录|開啟實例資料夾|開啟實例資料夾|Open folder|フォルダを開く
已准备就绪|已準備就緒|已準備就緒|Ready|準備完了
选择一个已安装的原版版本|選擇一個已安裝的原版版本|選擇一個已安裝的原版版本|Choose an installed vanilla version|インストール済みのバニラを選択
官方版本下载|官方版本下載|官方版本下載|Official downloads|公式バージョンのダウンロード
刷新官方列表|重新整理官方列表|重新整理官方列表|Refresh list|一覧を更新
获取官方列表|取得官方列表|取得官方列表|Fetch list|一覧を取得
正在获取…|正在取得…|正在取得…|Loading…|取得中…
选择官方版本|選擇官方版本|選擇官方版本|Choose a version|バージョンを選択
下载安装|下載安裝|下載安裝|Download & install|ダウンロードしてインストール
校验安装|校驗安裝|驗證安裝|Verify install|インストールを検証
正式版|正式版|正式版|Release|リリース
快照 / 预发布|快照 / 預覽版|快照 / 預覽版|Snapshots / pre-releases|スナップショット / プレリリース
全部版本|全部版本|全部版本|All versions|すべてのバージョン
历史版本|歷史版本|歷史版本|Old versions|旧バージョン
无|無|無|None|なし
安装加载器|安裝載入器|安裝載入器|Install loader|ローダーをインストール
我的实例|我的實例|我的實例|My instances|マイインスタンス
设为当前实例|設為目前實例|設為目前實例|Set as current|現在のインスタンスにする
游戏与资源|遊戲與資源|遊戲與資源|Game & resources|ゲームとリソース
请选择官方版本进行安装|請選擇官方版本進行安裝|請選擇官方版本進行安裝|Choose an official version to install|インストールする公式バージョンを選んでください
请先从 Minecraft 退出游戏|請先退出 Minecraft|請先退出 Minecraft|Please quit Minecraft first|先に Minecraft を終了してください
请先安装游戏版本|請先安裝遊戲版本|請先安裝遊戲版本|Install a game version first|先にゲームをインストールしてください
正在安装，请等待下载完成|正在安裝，請等待下載完成|正在安裝，請等待下載完成|Installing, please wait for the download|インストール中です。ダウンロード完了までお待ちください
游戏已退出（退出码 0）|遊戲已結束（結束碼 0）|遊戲已結束（結束代碼 0）|Game closed (exit code 0)|ゲームが終了しました（終了コード 0）
玩家名需为 3–16 个英文字母、数字或下划线|玩家名稱須為 3–16 個英文字母、數字或底線|玩家名稱須為 3–16 個英文字母、數字或底線|Player name must be 3–16 letters, digits or underscores|プレイヤー名は 3〜16 文字の英数字またはアンダースコアにしてください
请等待当前操作完成后管理模组|請等待目前操作完成後再管理模組|請等待目前操作完成後再管理模組|Wait for the current task before managing mods|現在の処理が終わってから Mod を管理してください
请等待当前操作完成后安装加载器|請等待目前操作完成後再安裝載入器|請等待目前操作完成後再安裝載入器|Wait for the current task before installing a loader|現在の処理が終わってからローダーをインストールしてください
请先安装并选择模组加载器实例|請先安裝並選擇模組載入器實例|請先安裝並選擇模組載入器實例|Install and select a mod loader instance first|先に Mod ローダーのインスタンスをインストールして選択してください
此实例正在运行或安装模组|此實例正在執行或安裝模組|此實例正在執行或安裝模組|This instance is running or installing mods|このインスタンスは実行中か Mod をインストール中です
此实例的模组正在被其他安装任务使用|此實例的模組正被其他安裝工作使用|此實例的模組正被其他安裝工作使用|Mods of this instance are in use by another task|このインスタンスの Mod は別の処理で使用中です
此模组含手动文件依赖，请从项目官网核对|此模組含手動檔案依賴，請到項目官網核對|此模組含手動檔案相依，請到專案官網核對|This mod has manual file dependencies; check its website|この Mod には手動の依存ファイルがあります。公式サイトで確認してください
此游戏版本暂无匹配的加载器|此遊戲版本暫無相符的載入器|此遊戲版本暫無相符的載入器|No loader available for this game version|このバージョンに対応するローダーはありません
选择版本后安装，将创建独立实例|選擇版本後安裝，將建立獨立實例|選擇版本後安裝，將建立獨立實例|Pick a version to create a separate instance|バージョンを選ぶと独立したインスタンスを作成します
正在安装或获取版本，请等待完成|正在安裝或取得版本，請稍候|正在安裝或取得版本，請稍候|Installing or fetching, please wait|インストールまたは取得中です。お待ちください
获取版本|取得版本|取得版本|Fetch versions|バージョンを取得
Java 运行时|Java 執行環境|Java 執行環境|Java runtime|Java ランタイム
依赖校验值无效|依賴校驗值無效|相依驗證值無效|Invalid dependency checksum|依存ファイルのチェックサムが無効です
依赖没有可下载文件|依賴沒有可下載的檔案|相依沒有可下載的檔案|Dependency has no downloadable file|依存関係にダウンロード可能なファイルがありません
依赖版本冲突，请选择其他模组版本|依賴版本衝突，請選擇其他模組版本|相依版本衝突，請選擇其他模組版本|Dependency conflict, choose another mod version|依存関係が衝突しています。別のバージョンを選んでください
安装器校验值无效|安裝程式校驗值無效|安裝程式驗證值無效|Invalid installer checksum|インストーラーのチェックサムが無効です
安装器没有生成版本配置|安裝程式沒有產生版本設定|安裝程式沒有產生版本設定|Installer produced no version profile|インストーラーがバージョン設定を作成しませんでした
官方清单校验失败|官方清單校驗失敗|官方清單驗證失敗|Official manifest check failed|公式マニフェストの検証に失敗しました
文件校验失败，请重试|檔案校驗失敗，請重試|檔案驗證失敗，請重試|File check failed, please retry|ファイル検証に失敗しました。再試行してください
无效 Maven 依赖|無效的 Maven 依賴|無效的 Maven 相依|Invalid Maven dependency|無効な Maven 依存関係
无效下载地址|無效的下載地址|無效的下載網址|Invalid download URL|無効なダウンロード URL
无效安装路径|無效的安裝路徑|無效的安裝路徑|Invalid install path|無効なインストール先
无效模组文件名|無效的模組檔名|無效的模組檔名|Invalid mod file name|無効な Mod ファイル名
无效版本名称|無效的版本名稱|無效的版本名稱|Invalid version name|無効なバージョン名
未知运行时文件类型|未知的執行環境檔案類型|未知的執行環境檔案類型|Unknown runtime file type|不明なランタイムファイル形式
模组依赖存在循环或数量过多|模組依賴出現循環或數量過多|模組相依出現循環或數量過多|Mod dependencies loop or are too many|Mod の依存関係が循環しているか多すぎます
此历史版本使用旧式启动参数，当前启动器尚未支持。|此歷史版本使用舊式啟動參數，目前啟動器尚未支援。|此歷史版本使用舊式啟動參數，目前啟動器尚未支援。|This old version uses legacy launch arguments, not supported yet.|この旧バージョンは古い起動引数を使うため、まだ対応していません。
此版本使用旧式原生库，当前启动器尚未支持。|此版本使用舊式原生程式庫，目前啟動器尚未支援。|此版本使用舊式原生函式庫，目前啟動器尚未支援。|This version uses legacy natives, not supported yet.|このバージョンは古いネイティブライブラリを使うため、まだ対応していません。
该版本使用旧式原生库配置。|該版本使用舊式原生程式庫設定。|該版本使用舊式原生函式庫設定。|This version uses a legacy natives setup.|このバージョンは古いネイティブ設定を使っています。
该版本缺少 JVM 类路径参数。|該版本缺少 JVM 類別路徑參數。|該版本缺少 JVM 類別路徑參數。|This version lacks JVM classpath arguments.|このバージョンには JVM クラスパス引数がありません。
版本 ID 不匹配|版本 ID 不相符|版本 ID 不相符|Version ID mismatch|バージョン ID が一致しません
版本继承存在循环|版本繼承出現循環|版本繼承出現循環|Version inheritance loop|バージョンの継承が循環しています
缺少完整的原版基础文件|缺少完整的原版基礎檔案|缺少完整的原版基礎檔案|Missing complete vanilla base files|バニラの基本ファイルが不足しています
缺少有效的 SHA-512 校验值|缺少有效的 SHA-512 校驗值|缺少有效的 SHA-512 驗證值|Missing a valid SHA-512 checksum|有効な SHA-512 チェックサムがありません
获取 Fabric 配置|取得 Fabric 設定|取得 Fabric 設定|Fetching Fabric profile|Fabric 設定を取得中
请先校验安装原版 Java 运行时|請先校驗安裝原版 Java 執行環境|請先驗證安裝原版 Java 執行環境|Verify the vanilla Java runtime first|先にバニラの Java ランタイムを検証してください
请求频繁，请稍后重试|請求太頻密，請稍後再試|請求太頻繁，請稍後再試|Too many requests, try again later|リクエストが多すぎます。しばらくしてから再試行してください
资源哈希无效|資源雜湊無效|資源雜湊無效|Invalid asset hash|アセットのハッシュが無効です
运行时链接路径无效|執行環境連結路徑無效|執行環境連結路徑無效|Invalid runtime link path|ランタイムのリンク先が無効です
星野藤 Hoshino Fuji|星野藤 Hoshino Fuji|星野藤 Hoshino Fuji|Hoshino Fuji|星野藤 Hoshino Fuji
点一下，和星野藤打个招呼|點一下，和星野藤打個招呼|點一下，和星野藤打個招呼|Click to say hi to Hoshino Fuji|クリックして星野藤にあいさつ
星野藤角色立绘，点击她会挥手|星野藤角色立繪，點擊她會揮手|星野藤角色立繪，點擊她會揮手|Hoshino Fuji illustration; click and she waves|星野藤のイラスト。クリックすると手を振ります
原版|原版|原版|Vanilla|バニラ
多选|多選|多選|Select|複数選択
全选|全選|全選|All|すべて選択
删除所选|刪除所選|刪除所選|Delete selected|選択項目を削除
删除这个实例|刪除這個實例|刪除這個實例|Delete this instance|このインスタンスを削除
删除|刪除|刪除|Delete|削除
取消|取消|取消|Cancel|キャンセル
知道了|知道了|知道了|OK|OK
删除实例|刪除實例|刪除實例|Delete instance|インスタンスを削除
有实例暂时不能删除|有實例暫時不能刪除|有實例暫時不能刪除|Some instances can't be deleted yet|削除できないインスタンスがあります
找不到版本文件夹|找不到版本資料夾|找不到版本資料夾|Version folder not found|バージョンフォルダが見つかりません
部分实例删除失败|部分實例刪除失敗|部分實例刪除失敗|Some instances failed to delete|一部のインスタンスを削除できませんでした
同时删除这些实例的存档、模组和设置（instances 文件夹里对应的目录）|同時刪除這些實例的存檔、模組和設定（instances 資料夾內對應的目錄）|同時刪除這些實例的存檔、模組和設定（instances 資料夾內對應的目錄）|Also delete their saves, mods and settings (their folders under instances)|セーブ・Mod・設定も削除する（instances 内の該当フォルダ）
文件会移到废纸篓 / 回收站，需要时还能找回。|檔案會移到垃圾桶 / 回收站，需要時仍可找回。|檔案會移到垃圾桶 / 資源回收筒，需要時仍可找回。|Files go to the Trash / Recycle Bin so you can restore them.|ファイルはゴミ箱に移動されるので、後で戻せます。
注意：删除后无法恢复。|注意：刪除後無法復原。|注意：刪除後無法復原。|Note: this can't be undone.|注意：削除後は元に戻せません。
还没有安装任何实例，去上面下载一个吧|還沒有安裝任何實例，到上面下載一個吧|還沒有安裝任何實例，到上面下載一個吧|No instances yet — download one above|インスタンスがありません。上からダウンロードしましょう
打开当前实例的文件夹：地图放进 saves，资源包放进 resourcepacks，光影放进 shaderpacks|開啟目前實例的資料夾：地圖放入 saves，資源包放入 resourcepacks，光影放入 shaderpacks|開啟目前實例的資料夾：地圖放入 saves，資源包放入 resourcepacks，光影放入 shaderpacks|Opens the instance folder: maps go in saves, resource packs in resourcepacks, shaders in shaderpacks|インスタンスのフォルダを開きます。ワールドは saves、リソースパックは resourcepacks、シェーダーは shaderpacks へ
正在安装加载器，请等待完成|正在安裝載入器，請稍候|正在安裝載入器，請稍候|Installing loader, please wait|ローダーをインストール中です。お待ちください
模组|模組|模組|Mods|Mod
资源包 · 材质包|資源包 · 材質包|資源包 · 材質包|Resource packs|リソースパック
资源包|資源包|資源包|Resource packs|リソースパック
光影包|光影包|光影包|Shaders|シェーダー
模组文件|模組檔案|模組檔案|Mod files|Mod ファイル
所有文件|所有檔案|所有檔案|All files|すべてのファイル
在线下载|線上下載|線上下載|Browse online|オンラインで探す
来自 Modrinth|來自 Modrinth|來自 Modrinth|from Modrinth|Modrinth から
更多下载渠道|更多下載渠道|更多下載管道|More sources|ほかの入手先
搜索|搜尋|搜尋|Search|検索
游戏版本|遊戲版本|遊戲版本|Game version|ゲームバージョン
不限|不限|不限|Any|指定なし
上一页|上一頁|上一頁|Previous|前へ
下一页|下一頁|下一頁|Next|次へ
下载…|下載…|下載…|Download…|ダウンロード…
先在上面选一个项目|先在上面選一個項目|先在上面選一個專案|Pick a project above first|先に上で項目を選んでください
没有找到内容|找不到內容|找不到內容|Nothing found|見つかりませんでした
实例|實例|實例|Instance|インスタンス
本地导入|本機匯入|本機匯入|Import file|ファイルから追加
打开文件夹|開啟資料夾|開啟資料夾|Open folder|フォルダを開く
已安装的模组|已安裝的模組|已安裝的模組|Installed mods|インストール済み Mod
已安装的资源包|已安裝的資源包|已安裝的資源包|Installed resource packs|インストール済みリソースパック
已安装的光影包|已安裝的光影包|已安裝的光影包|Installed shaders|インストール済みシェーダー
搜索模组名称，留空查看热门|搜尋模組名稱，留空查看熱門|搜尋模組名稱，留空查看熱門|Search mods, leave empty for popular|Mod を検索（空欄で人気順）
搜索资源包 / 材质包名称，留空查看热门|搜尋資源包 / 材質包名稱，留空查看熱門|搜尋資源包 / 材質包名稱，留空查看熱門|Search resource packs, leave empty for popular|リソースパックを検索（空欄で人気順）
搜索光影包名称，留空查看热门|搜尋光影包名稱，留空查看熱門|搜尋光影包名稱，留空查看熱門|Search shaders, leave empty for popular|シェーダーを検索（空欄で人気順）
还没有实例，请先在资源中心下载一个游戏版本。|還沒有實例，請先在資源中心下載一個遊戲版本。|還沒有實例，請先在資源中心下載一個遊戲版本。|No instance yet. Download a game version in Resources first.|インスタンスがありません。先にリソースでゲームをダウンロードしてください。
这是原版实例，不能直接装模组。请先在资源中心给它装上 Fabric、Forge 或 NeoForge。|這是原版實例，不能直接安裝模組。請先在資源中心為它安裝 Fabric、Forge 或 NeoForge。|這是原版實例，不能直接安裝模組。請先在資源中心為它安裝 Fabric、Forge 或 NeoForge。|This is a vanilla instance and can't take mods. Install Fabric, Forge or NeoForge in Resources first.|バニラのインスタンスには Mod を入れられません。先にリソースで Fabric・Forge・NeoForge を入れてください。
原版不支持光影，需要先装 Iris 或 OptiFine 等模组。|原版不支援光影，需要先安裝 Iris 或 OptiFine 等模組。|原版不支援光影，需要先安裝 Iris 或 OptiFine 等模組。|Vanilla can't run shaders; install a mod such as Iris or OptiFine first.|バニラではシェーダーを使えません。Iris や OptiFine などの Mod が必要です。
正在搜索 Modrinth…|正在搜尋 Modrinth…|正在搜尋 Modrinth…|Searching Modrinth…|Modrinth を検索中…
没有找到内容，试试别的关键词或清空游戏版本|找不到內容，試試其他關鍵字或清空遊戲版本|找不到內容，試試其他關鍵字或清空遊戲版本|Nothing found. Try other words or clear the game version.|見つかりません。別のキーワードを試すか、ゲームバージョンを空にしてください
选择左侧项目查看可下载的版本|選擇左側項目查看可下載的版本|選擇左側專案查看可下載的版本|Pick a project to see downloadable versions|項目を選ぶとダウンロードできるバージョンが表示されます
正在获取匹配版本…|正在取得相符版本…|正在取得相符版本…|Fetching matching versions…|対応バージョンを取得中…
此项目没有匹配当前条件的版本|此項目沒有符合目前條件的版本|此專案沒有符合目前條件的版本|No versions match the current filters|条件に合うバージョンがありません
选好版本后点「下载…」|選好版本後按「下載…」|選好版本後按「下載…」|Choose a version, then click “Download…”|バージョンを選んで「ダウンロード…」を押してください
安装到游戏|安裝到遊戲|安裝到遊戲|Install to game|ゲームに入れる
保存到本地…|儲存到本機…|儲存到本機…|Save to disk…|PC に保存…
还没有可以安装的实例，只能保存到本地。|還沒有可以安裝的實例，只能儲存到本機。|還沒有可以安裝的實例，只能儲存到本機。|There's no instance to install into, so it can only be saved to disk.|入れられるインスタンスがないので、PC への保存のみできます。
保存到本地：自己选择保存位置，之后可以用「本地导入」装进任意实例。|儲存到本機：自行選擇儲存位置，之後可用「本機匯入」裝進任何實例。|儲存到本機：自行選擇儲存位置，之後可用「本機匯入」裝進任何實例。|Save to disk: choose where to save it; you can add it to any instance later with “Import file”.|PC に保存：保存先を選べます。あとで「ファイルから追加」で好きなインスタンスに入れられます。
选择保存位置|選擇儲存位置|選擇儲存位置|Choose where to save|保存先を選択
没有选择实例|沒有選擇實例|沒有選擇實例|No instance selected|インスタンスが選ばれていません
这个实例还没有模组|這個實例還沒有模組|這個實例還沒有模組|No mods in this instance yet|このインスタンスには Mod がありません
这个实例还没有资源包|這個實例還沒有資源包|這個實例還沒有資源包|No resource packs in this instance yet|このインスタンスにはリソースパックがありません
这个实例还没有光影包|這個實例還沒有光影包|這個實例還沒有光影包|No shaders in this instance yet|このインスタンスにはシェーダーがありません
文件夹|資料夾|資料夾|Folder|フォルダ
模组与实例的游戏版本或加载器不兼容：{0}|模組與實例的遊戲版本或載入器不相容：{0}|模組與實例的遊戲版本或載入器不相容：{0}|Mod doesn't match the instance's game version or loader: {0}|Mod がインスタンスのバージョンまたはローダーに合いません：{0}
Minecraft {0} 运行中|Minecraft {0} 執行中|Minecraft {0} 執行中|Minecraft {0} is running|Minecraft {0} を実行中
{0} 已安装，可以下载模组并启动|{0} 已安裝，可以下載模組並啟動|{0} 已安裝，可以下載模組並啟動|{0} installed — add mods or play|{0} をインストールしました。Mod を追加して起動できます
{0} 已安装，可以启动游戏|{0} 已安裝，可以啟動遊戲|{0} 已安裝，可以啟動遊戲|{0} installed — ready to play|{0} をインストールしました。起動できます
{0} 已安装，已设为当前实例|{0} 已安裝，並設為目前實例|{0} 已安裝，並設為目前實例|{0} installed and set as current|{0} をインストールし、現在のインスタンスにしました
{0}安装失败：{1}|{0} 安裝失敗：{1}|{0} 安裝失敗：{1}|{0} install failed: {1}|{0} のインストールに失敗しました：{1}
下载 Fabric 依赖 {0}|下載 Fabric 依賴 {0}|下載 Fabric 相依 {0}|Downloading Fabric libraries {0}|Fabric ライブラリをダウンロード中 {0}
下载 {0} 官方安装器|下載 {0} 官方安裝程式|下載 {0} 官方安裝程式|Downloading the official {0} installer|{0} 公式インストーラーをダウンロード中
下载模组及依赖 {0}|下載模組及依賴 {0}|下載模組及相依 {0}|Downloading mods and dependencies {0}|Mod と依存関係をダウンロード中 {0}
下载渠道返回 HTTP {0}|下載來源回應 HTTP {0}|下載來源回應 HTTP {0}|Download source returned HTTP {0}|ダウンロード元が HTTP {0} を返しました
原版 {0}|原版 {0}|原版 {0}|Vanilla {0}|バニラ {0}
启动失败：{0}|啟動失敗：{0}|啟動失敗：{0}|Launch failed: {0}|起動に失敗しました：{0}
基础游戏版本  {0}|基礎遊戲版本  {0}|基礎遊戲版本  {0}|Base game version  {0}|ベースバージョン  {0}
安装器失败，详情 {0}|安裝程式失敗，詳情 {0}|安裝程式失敗，詳情 {0}|Installer failed, details: {0}|インストーラーが失敗しました。詳細 {0}
安装器超时，可重试。详情 {0}|安裝程式逾時，可重試。詳情 {0}|安裝程式逾時，可重試。詳情 {0}|Installer timed out, please retry. Details: {0}|インストーラーがタイムアウトしました。詳細 {0}
安装失败：{0}|安裝失敗：{0}|安裝失敗：{0}|Install failed: {0}|インストールに失敗しました：{0}
安装模组加载器 · {0}|安裝模組載入器 · {0}|安裝模組載入器 · {0}|Install mod loader · {0}|Mod ローダーをインストール · {0}
官方列表获取失败，请重试：{0}|官方列表取得失敗，請重試：{0}|官方列表取得失敗，請重試：{0}|Couldn't fetch the official list, retry: {0}|公式一覧の取得に失敗しました：{0}
官方未提供 {0} 的 {1}|官方未提供 {0} 的 {1}|官方未提供 {0} 的 {1}|No official {1} for {0}|{0} の {1} は公式に提供されていません
实例暂不可用：{0}|實例暫時無法使用：{0}|實例暫時無法使用：{0}|Instance unavailable: {0}|インスタンスを使用できません：{0}
尚未支持启动参数 {0}|尚未支援啟動參數 {0}|尚未支援啟動參數 {0}|Launch argument not supported yet: {0}|起動引数 {0} にはまだ対応していません
找不到兼容的必需依赖 {0}|找不到相容的必需依賴 {0}|找不到相容的必要相依 {0}|No compatible required dependency: {0}|互換性のある必須依存関係が見つかりません：{0}
文件校验失败：{0}|檔案校驗失敗：{0}|檔案驗證失敗：{0}|File check failed: {0}|ファイル検証に失敗しました：{0}
无法打开实例目录：{0}|無法開啟實例資料夾：{0}|無法開啟實例資料夾：{0}|Can't open the instance folder: {0}|インスタンスのフォルダを開けません：{0}
无法设置执行权限：{0}|無法設定執行權限：{0}|無法設定執行權限：{0}|Can't set execute permission: {0}|実行権限を設定できません：{0}
查找官方运行时 {0}|尋找官方執行環境 {0}|尋找官方執行環境 {0}|Looking up official runtime {0}|公式ランタイムを確認中 {0}
模组文件名冲突：{0}|模組檔名衝突：{0}|模組檔名衝突：{0}|Mod file name conflict: {0}|Mod ファイル名が重複しています：{0}
正在安装 {0}|正在安裝 {0}|正在安裝 {0}|Installing {0}|{0} をインストール中
正在获取 {0} 官方版本…|正在取得 {0} 官方版本…|正在取得 {0} 官方版本…|Fetching official {0} versions…|{0} の公式バージョンを取得中…
获取失败：{0}|取得失敗：{0}|取得失敗：{0}|Fetch failed: {0}|取得に失敗しました：{0}
读取本地版本失败：{0}|讀取本機版本失敗：{0}|讀取本機版本失敗：{0}|Couldn't read local versions: {0}|ローカルのバージョンを読み込めません：{0}
读取版本配置 {0}|讀取版本設定 {0}|讀取版本設定 {0}|Reading version profile {0}|バージョン設定を読み込み中 {0}
运行 {0} 官方安装器，正在修补客户端…|執行 {0} 官方安裝程式，正在修補用戶端…|執行 {0} 官方安裝程式，正在修補用戶端…|Running the official {0} installer, patching the client…|{0} 公式インストーラーでクライアントを修正中…
缺少依赖 {0}，请重新安装或校验此加载器。|缺少依賴 {0}，請重新安裝或校驗此載入器。|缺少相依 {0}，請重新安裝或驗證此載入器。|Missing dependency {0}; reinstall or verify this loader.|依存関係 {0} がありません。ローダーを再インストールまたは検証してください。
缺少资源索引 {0}，请在官方列表中选择此版本并校验安装。|缺少資源索引 {0}，請在官方列表選擇此版本並校驗安裝。|缺少資源索引 {0}，請在官方列表選擇此版本並驗證安裝。|Missing asset index {0}; pick this version in the official list and verify.|アセットインデックス {0} がありません。公式一覧で検証してください。
缺少运行时 {0}，请在官方列表中选择此版本并安装。|缺少執行環境 {0}，請在官方列表選擇此版本並安裝。|缺少執行環境 {0}，請在官方列表選擇此版本並安裝。|Missing runtime {0}; pick this version in the official list and install.|ランタイム {0} がありません。公式一覧からインストールしてください。
游戏异常退出（退出码 {0}）|遊戲異常結束（結束碼 {0}）|遊戲異常結束（結束代碼 {0}）|Game crashed (exit code {0})|ゲームが異常終了しました（終了コード {0}）
共 {0} 个|共 {0} 個|共 {0} 個|{0} total|全 {0} 件
已选 {0} 个|已選 {0} 個|已選 {0} 個|{0} selected|{0} 件選択
删除「{0}」？|刪除「{0}」？|刪除「{0}」？|Delete “{0}”?|「{0}」を削除しますか？
删除这 {0} 个实例？|刪除這 {0} 個實例？|刪除這 {0} 個實例？|Delete these {0} instances?|この {0} 個のインスタンスを削除しますか？
删除这 {0} 个文件？|刪除這 {0} 個檔案？|刪除這 {0} 個檔案？|Delete these {0} files?|この {0} 個のファイルを削除しますか？
删除 {0} 个|刪除 {0} 個|刪除 {0} 個|Delete {0}|{0} 件削除
正在删除 {0} 个实例…|正在刪除 {0} 個實例…|正在刪除 {0} 個實例…|Deleting {0} instances…|{0} 個のインスタンスを削除中…
已删除 {0} 个实例|已刪除 {0} 個實例|已刪除 {0} 個實例|Deleted {0} instances|{0} 個のインスタンスを削除しました
已删除 {0} 个文件|已刪除 {0} 個檔案|已刪除 {0} 個檔案|Deleted {0} files|{0} 個のファイルを削除しました
已删除 {0}|已刪除 {0}|已刪除 {0}|Deleted {0}|{0} を削除しました
有 {0} 个实例删除失败|有 {0} 個實例刪除失敗|有 {0} 個實例刪除失敗|{0} instances failed to delete|{0} 個のインスタンスを削除できませんでした
「{0}」被 {1} 使用|「{0}」正被 {1} 使用|「{0}」正被 {1} 使用|“{0}” is used by {1}|「{0}」は {1} が使用中です
{0}\n\n请先删除（或一起勾选）依赖它的实例。|{0}\n\n請先刪除（或一併勾選）依賴它的實例。|{0}\n\n請先刪除（或一併勾選）相依它的實例。|{0}\n\nDelete (or also select) the instances that depend on it first.|{0}\n\n先にそれに依存するインスタンスを削除（または一緒に選択）してください。
{0}\n没有删除任何文件。|{0}\n沒有刪除任何檔案。|{0}\n沒有刪除任何檔案。|{0}\nNothing was deleted.|{0}\nファイルは削除されていません。
将删除版本文件：\n{0}|將刪除版本檔案：\n{0}|將刪除版本檔案：\n{0}|Version files to delete:\n{0}|削除するバージョンファイル：\n{0}
同时删除这个实例的存档、模组和设置\n{0}|同時刪除這個實例的存檔、模組和設定\n{0}|同時刪除這個實例的存檔、模組和設定\n{0}|Also delete this instance's saves, mods and settings\n{0}|このインスタンスのセーブ・Mod・設定も削除する\n{0}
找不到版本文件夹，将跳过：{0}|找不到版本資料夾，將略過：{0}|找不到版本資料夾，將略過：{0}|Version folder not found, skipping: {0}|バージョンフォルダが見つからないためスキップ：{0}
找到 {0} 个项目 · 第 {1} 页|找到 {0} 個項目 · 第 {1} 頁|找到 {0} 個專案 · 第 {1} 頁|{0} results · page {1}|{0} 件 · {1} ページ目
下载「{0}」|下載「{0}」|下載「{0}」|Download “{0}”|「{0}」をダウンロード
当前实例「{0}」是原版，不能安装模组，只能保存到本地。|目前實例「{0}」是原版，不能安裝模組，只能儲存到本機。|目前實例「{0}」是原版，不能安裝模組，只能儲存到本機。|The instance “{0}” is vanilla and can't take mods, so it can only be saved to disk.|「{0}」はバニラなので Mod を入れられません。PC への保存のみできます。
安装到游戏：放进实例「{0}」的 mods 文件夹，会自动补齐必需的前置模组。|安裝到遊戲：放入實例「{0}」的 mods 資料夾，並自動補齊必需的前置模組。|安裝到遊戲：放入實例「{0}」的 mods 資料夾，並自動補齊必要的前置模組。|Install to game: goes into the mods folder of “{0}”, with required dependencies added automatically.|ゲームに入れる：「{0}」の mods フォルダに入れ、必要な前提 Mod も自動で追加します。
安装到游戏：放进实例「{0}」的 {1} 文件夹。|安裝到遊戲：放入實例「{0}」的 {1} 資料夾。|安裝到遊戲：放入實例「{0}」的 {1} 資料夾。|Install to game: goes into the {1} folder of “{0}”.|ゲームに入れる：「{0}」の {1} フォルダに入れます。
正在下载并校验 {0}|正在下載並校驗 {0}|正在下載並驗證 {0}|Downloading and verifying {0}|{0} をダウンロードして検証中
已安装「{0}」到 {1}|已將「{0}」安裝到 {1}|已將「{0}」安裝到 {1}|Installed “{0}” to {1}|「{0}」を {1} に入れました
已保存到 {0}|已儲存到 {0}|已儲存到 {0}|Saved to {0}|{0} に保存しました
已导入 {0} 个文件|已匯入 {0} 個檔案|已匯入 {0} 個檔案|Imported {0} files|{0} 個のファイルを追加しました
已停用 · {0}|已停用 · {0}|已停用 · {0}|Disabled · {0}|無効 · {0}
读取已安装内容失败：{0}|讀取已安裝內容失敗：{0}|讀取已安裝內容失敗：{0}|Couldn't read installed content: {0}|インストール済みの内容を読み込めません：{0}
操作失败：{0}|操作失敗：{0}|操作失敗：{0}|Failed: {0}|失敗しました：{0}
选择要导入的{0}|選擇要匯入的{0}|選擇要匯入的{0}|Choose {0} to import|追加する{0}を選択
{0}  ·  {1}|{0}  ·  {1}|{0}  ·  {1}|{0}  ·  {1}|{0}  ·  {1}
"""
R += r"""
导入地图|匯入地圖|匯入地圖|Import map|ワールドを追加
还没有实例|還沒有實例|還沒有實例|No instances yet|インスタンスがありません
请先在上面下载一个游戏版本，再导入地图。|請先在上面下載一個遊戲版本，再匯入地圖。|請先在上面下載一個遊戲版本，再匯入地圖。|Download a game version above first, then import the map.|先に上でゲームをダウンロードしてから、ワールドを追加してください。
导入到哪个实例（游戏版本）|匯入到哪個實例（遊戲版本）|匯入到哪個實例（遊戲版本）|Import into which instance (game version)|追加先のインスタンス（ゲームバージョン）
地图可以是一个存档文件夹（里面有 level.dat），也可以是下载来的 .zip 压缩包。|地圖可以是一個存檔資料夾（內有 level.dat），也可以是下載得來的 .zip 壓縮檔。|地圖可以是一個存檔資料夾（內含 level.dat），也可以是下載來的 .zip 壓縮檔。|A map can be a world folder (containing level.dat) or a downloaded .zip file.|ワールドは level.dat を含むフォルダ、またはダウンロードした .zip ファイルです。
选择地图文件夹…|選擇地圖資料夾…|選擇地圖資料夾…|Choose folder…|フォルダを選択…
选择 .zip 压缩包…|選擇 .zip 壓縮檔…|選擇 .zip 壓縮檔…|Choose .zip…|.zip を選択…
选择地图文件夹|選擇地圖資料夾|選擇地圖資料夾|Choose a map folder|ワールドのフォルダを選択
选择地图压缩包|選擇地圖壓縮檔|選擇地圖壓縮檔|Choose a map archive|ワールドの .zip を選択
地图压缩包|地圖壓縮檔|地圖壓縮檔|Map archive|ワールドのアーカイブ
正在导入地图…|正在匯入地圖…|正在匯入地圖…|Importing map…|ワールドを追加中…
导入地图失败|匯入地圖失敗|匯入地圖失敗|Map import failed|ワールドを追加できませんでした
这个文件夹里没有找到 level.dat，不像是 Minecraft 地图|這個資料夾內找不到 level.dat，不像是 Minecraft 地圖|這個資料夾內找不到 level.dat，不像是 Minecraft 地圖|No level.dat in this folder — it doesn't look like a Minecraft map|このフォルダに level.dat がありません。Minecraft のワールドではないようです
压缩包里没有找到 level.dat，不像是 Minecraft 地图|壓縮檔內找不到 level.dat，不像是 Minecraft 地圖|壓縮檔內找不到 level.dat，不像是 Minecraft 地圖|No level.dat in this archive — it doesn't look like a Minecraft map|.zip に level.dat がありません。Minecraft のワールドではないようです
已导入地图「{0}」到 {1}|已將地圖「{0}」匯入到 {1}|已將地圖「{0}」匯入到 {1}|Imported map “{0}” into {1}|ワールド「{0}」を {1} に追加しました
已导入地图「{0}」|已匯入地圖「{0}」|已匯入地圖「{0}」|Imported map “{0}”|ワールド「{0}」を追加しました
启动「{0}」后，在「单人游戏」列表里就能看到这张地图。|啟動「{0}」後，在「單人遊戲」列表中就能看到這張地圖。|啟動「{0}」後，在「單人遊戲」列表中就能看到這張地圖。|Launch “{0}” and you'll find it in the Singleplayer list.|「{0}」を起動すると、シングルプレイの一覧に表示されます。
导入地图失败：{0}|匯入地圖失敗：{0}|匯入地圖失敗：{0}|Map import failed: {0}|ワールドを追加できませんでした：{0}
压缩包里有不安全的路径：{0}|壓縮檔內有不安全的路徑：{0}|壓縮檔內有不安全的路徑：{0}|Unsafe path in archive: {0}|.zip に安全でないパスがあります：{0}
"""

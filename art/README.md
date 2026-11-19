# 美术源文件与历史加工

`source/design.png` 是交接提供的原始界面设计图；`src/main/resources/theme/` 是本版完整运行素材，已纳入版本管理。角色原始设计、图层和代码加工脚本均随源码公开；许可分别见根目录 LICENSE-ASSETS 与 LICENSE，署名 hoshinofuji。

`legacy/` 保留交接的 Python 美术加工方法，包括手臂轮廓、闭眼贴片、图标、猫咪分层、水面与头发位移场、翻译词条和离线预览。旧工作目录已移除；读取原始设计的路径固定为 `source/design.png`。脚本在当前目录输出中间图，运行前应切换到独立的 `art/output/`，避免覆盖正式素材。需要 Python、Pillow、NumPy 和 OpenCV；它们只是可选美术工具，启动器构建不使用这些第三方 Python 包。

这些是历史加工步骤，不是逐字节可复现的完整素材流水线。交接缺少 `charmask.png` 等人工中间遮罩；`warp.py` 在缺少遮罩时明确报错。需要自行制作人物遮罩或以已经开源的运行图层为起点编辑。不要为了构建启动器去运行历史加工脚本，正式构建直接复制资源目录中的素材。

历史步骤参考：`art_v2.py` 产生去手臂底图，复制为 `poster-base-v2.png`；`art_v3.py` 提取猫和水面遮罩，其底图复制为 `poster-base-v3.png`；`plus.py` 调整发饰，其底图复制为 `poster-base-plus.png`；`shoulder2.py` 输出最终底图及 `arm.png`；`eyes.py` 输出 `eyes-closed.png`；`ui_assets.py` 输出装饰图标；`warp.py` 使用人物和水面遮罩输出位移场。运行资源对应的手臂和闭眼文件名分别为 `poster-arm.png`、`poster-blink.png`，坐标见 SkinView.java。

`i18n_rows.py` 保存翻译源字符串；正式词表是 theme/i18n.tsv。`cp.py` 是历史常量池检查工具，只对显式指定的本地 class 文件读字符串，不参与构建或运行。`proto.py` 为离线预览实验。所有脚本均不会自动上传文件。

AI 辅助及 Minecraft 衍生元素的权利范围见 LICENSE-ASSETS 与 THIRD_PARTY_NOTICES。源码公开不代表把第三方 Minecraft 商标或底层游戏图案转为无条件授权。

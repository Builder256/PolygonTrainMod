<div align="center">

# PolygonTrainMod
![Minecraft Version: 1.21.1](https://img.shields.io/badge/Minecraft_Version-1.21.1-informational)
![Mod Loader: NeoForge](https://img.shields.io/badge/Mod_Loader-NeoForge-d7742f)

PolygonTrainModは、Minecraftに実寸大のポリゴンベースの鉄道システムの実装を目指すModです。
## このModの目的
</div>

このModの目標は、[RealTrainMod](https://www.curseforge.com/minecraft/mc-mods/realtrainmod)を最新のMinecraftに移植することです。そのために、このModの開発を通して、RealTrainModで使用されている各種機能が現代のMinecraftで再現可能であることを証明し、ngt5479氏に屋号をお貸しいただく交渉の材料とすることが目的です。

特に、アドオン関連の実装は困難を極めると考えられます。コンテンツを追加する手段が、クライアントサイドの「リソースパック」と、サーバーサイドの「データパック」に完全に二分されている現代のバージョンでは、RTMのモデルパックのような完全に同一のファイルをサーバー、クライアント両方で利用することは考慮されておらず、アセット管理を一から実装する必要がある可能性があります。

しかしながら、これらの困難を必ずや乗り越え、RTMを移植することには大きな意義があると考えています。

この理念にご賛同いただける方は、Issue、Pull Requestなどでご協力いただきますようお願い申し上げます。

## 開発環境のセットアップ
1. [IntelliJ IDEA](https://www.jetbrains.com/ja-jp/idea/)をインストール
2. ソースを開く
3. 「ファイル」->「プロジェクト構造」->「プロジェクト」でSDKに「jbr-21 JetBrains Runtime 21.0.8」を設定
4. おわり。

これで終わり！？マジか！NeoForgeサイコー！！！！

## 開発方針
私がMod開発初心者のため、まずはRTMに実装されている車両以外の要素の再現を目指します。

現段階では、切符と改札機、券売機の再現が目標です。

## 貢献
- 小さなPRも歓迎します。
- コード以外での、ブロックモデル・アイテムモデルなどのアセット提供も歓迎します。
- 質問があれば、お気軽にIssueを建ててください。

---

Installation information
=======

This template repository can be directly cloned to get you started with a new
mod. Simply create a new repository cloned from this one, by following the
instructions provided by [GitHub](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-repository-from-a-template).

Once you have your clone, simply open the repository in the IDE of your choice. The usual recommendation for an IDE is either IntelliJ IDEA or Eclipse.

If at any point you are missing libraries in your IDE, or you've run into problems you can
run `gradlew --refresh-dependencies` to refresh the local cache. `gradlew clean` to reset everything 
{this does not affect your code} and then start the process again.

Mapping Names:
============
By default, the MDK is configured to use the official mapping names from Mojang for methods and fields 
in the Minecraft codebase. These names are covered by a specific license. All modders should be aware of this
license. For the latest license text, refer to the mapping file itself, or the reference copy here:
https://github.com/NeoForged/NeoForm/blob/main/Mojang.md

Additional Resources: 
==========
Community Documentation: https://docs.neoforged.net/  
NeoForged Discord: https://discord.neoforged.net/

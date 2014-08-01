package bootstrap.liftweb

import _root_.net.liftweb.util._
import _root_.net.liftweb.common._
import _root_.net.liftweb.http._
import _root_.net.liftweb.http.provider._
import _root_.net.liftweb.sitemap._
import _root_.net.liftweb.sitemap.Loc._
import Helpers._
import _root_.net.liftweb.mapper.{DB, ConnectionManager, Schemifier, DefaultConnectionIdentifier, StandardDBVendor}
import _root_.java.sql.{Connection, DriverManager}
import _root_.com.andy.model._


/**
 * A class that's instantiated early and run.  It allows the application
 * to modify lift's environment
 * 系统首先加载和运行的类,他容许应用修改lift的环境
 */
class Boot {
  def boot {
    //判断是否可以从jndi读取数据库的驱动
    if (!DB.jndiJdbcConnAvailable_?) {
      val vendor =
      //标准数据库供应商(vendor)
        new StandardDBVendor(Props.get("db.driver") openOr "org.h2.Driver",
          Props.get("db.url") openOr
            "jdbc:h2:lift_proto.db;AUTO_SERVER=TRUE",
          Props.get("db.user"), Props.get("db.password"))

      //添加到(当LiftServlet调用destory方法是调用的钩子(Hooks))的RulesSeq中
      //vendor.closeAllConnections_! 关闭所有此数据库的连接
      LiftRules.unloadHooks.append(vendor.closeAllConnections_! _)

      //数据库连接管理,给vendor打上jndiName = "lift"的标记
      DB.defineConnectionManager(DefaultConnectionIdentifier, vendor)
    }

    // where to search snippet
    //定义在什么地方查找代码段(snippet)
    LiftRules.addToPackages("com.andy")
    //定义数据库meta的映射类,此处为User
    Schemifier.schemify(true, Schemifier.infoF _, User)

    // Build SiteMap
    //建立(网站地图)sitmap
    def sitemap() = SiteMap(
      Menu("首页") / "index" :: // Simple menu form,简单的菜单形式,链接到根目录的index.html
        // Menu with special Link ,有特殊链接的菜单
        Menu(Loc("Static", Link(List("static"), true, "/static/index"),
          "Static Content(静态内容显示)")) ::
        // Menu entries for the User management stuff user管理功能的链接,lift内置了用户管理模块
        User.sitemap: _*)
    //设置sitmap产生的函数
    LiftRules.setSiteMapFunc(sitemap)

    /*
     * Show the spinny image when an Ajax call starts
     * 当调用ajax开始时显示的图片
     * Full is a Box containing a value
     * Full是一个包含值的box
     */
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)

    /*
     * Make the spinny image go away when it ends
     * 当ajax调用结束时隐藏小图片
     */
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)

    //在request处理之前调用的函数集合
    LiftRules.early.append(makeUtf8)

    /**
     * Put a test for being logged in into this function
     * 当记录日志的时候,放一个test进这个函数
     */
    LiftRules.loggedInTest = Full(() => User.loggedIn_?)

    /**
     * Build a LoanWrapper to pass into S.addAround() to make requests for
     * the DefaultConnectionIdentifier transactional for the complete HTTP request
     *建立一个LoanWrapper进入S.addAround()请求,以保证
     *DefaultConnectionIdentifier事务的完整的HTTP请求
     */
    S.addAround(DB.buildLoanWrapper)
  }

  /**
   * Force the request to be UTF-8
   * 强制request请求转换成UTF-8编码
   */
  private def makeUtf8(req: HTTPRequest) {
    req.setCharacterEncoding("UTF-8")
  }
}

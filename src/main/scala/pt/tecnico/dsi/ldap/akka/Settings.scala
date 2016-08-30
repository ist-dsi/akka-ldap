package pt.tecnico.dsi.ldap.akka

import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import pt.tecnico.dsi.ldap.{Settings => LdapSettings}

import scala.concurrent.duration.Duration

/**
  * This class holds all the settings to customize akka-ldap.
  * By default these settings are read from the Config obtained with `ConfigFactory.load()`.
  *
  * You can change the settings in multiple ways:
  *
  *  - Change them in the default configuration file (e.g. application.conf)
  *  - Pass a different config holding your configurations: {{{
  *       new Settings(yourConfig)
  *     }}}
  *     However it will be more succinct to pass your config directly to LadpActor: {{{
  *      context.actorOf(Props(classOf[LdapActor], yourConfig))
  *     }}}
  *  - Extend this class overriding the settings you want to redefine {{{
  *      object YourSettings extends Settings() {
  *        override val performDeduplication: Boolean = true
  *      }
  *      context.actorOf(Props(classOf[LdapActor], YourSettings))
  *    }}}
  *
  * @param config
  */
class Settings(config: Config = ConfigFactory.load()) {
  val akkaLdapConfig: Config = {
    val reference = ConfigFactory.defaultReference()
    val finalConfig = config.withFallback(reference)
    finalConfig.checkValid(reference, "akka-ldap")
    finalConfig.getConfig("akka-ldap")
  }
  import akkaLdapConfig._

  val removeDelay = Duration(getDuration("remove-delay", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)

  val saveSnapshotRoughlyEveryXMessages = getInt("save-snapshot-roughly-every-X-messages")

  val ldapSettings: LdapSettings = {
    val path = "ldap"
    if (akkaLdapConfig.hasPath(path)) {
      val c = if (config.hasPath(path)) {
        akkaLdapConfig.getConfig(path).withFallback(config.getConfig(path))
      } else {
        akkaLdapConfig.getConfig(path)
      }
      new LdapSettings(c.atPath(path))
    } else if (config.hasPath(path)) {
      new LdapSettings(config.getConfig(path).atPath(path))
    } else {
      new LdapSettings()
    }
  }

  override def toString: String = akkaLdapConfig.root.render
}

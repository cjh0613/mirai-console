@file:Suppress("NOTHING_TO_INLINE")

package net.mamoe.mirai.console.command

import net.mamoe.mirai.Bot
import net.mamoe.mirai.console.utils.fuzzySearchMember
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.SingleMessage
import net.mamoe.mirai.message.data.content
import kotlin.contracts.contract

/**
 * this output type of that arg
 * input is always String
 */
abstract class CommandArgParser<out T : Any> {
    abstract fun parse(s: String, sender: CommandSender): T
    open fun parse(s: SingleMessage, sender: CommandSender): T = parse(s.content, sender)
}

@Suppress("unused")
@JvmSynthetic
inline fun CommandArgParser<*>.illegalArgument(message: String, cause: Throwable? = null): Nothing {
    throw ParserException(message, cause)
}

@JvmSynthetic
inline fun CommandArgParser<*>.checkArgument(
    condition: Boolean,
    crossinline message: () -> String = { "Check failed." }
) {
    contract {
        returns() implies condition
    }
    if (!condition) illegalArgument(message())
}

/**
 * 创建匿名 [CommandArgParser]
 */
@Suppress("FunctionName")
@JvmSynthetic
inline fun <T : Any> CommandArgParser(
    crossinline parser: CommandArgParser<T>.(s: String, sender: CommandSender) -> T
): CommandArgParser<T> = object : CommandArgParser<T>() {
    override fun parse(s: String, sender: CommandSender): T = parser(s, sender)
}


/**
 * 在解析参数时遇到的 _正常_ 错误. 如参数不符合规范.
 */
class ParserException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)


object IntArgParser : CommandArgParser<Int>() {
    override fun parse(s: String, sender: CommandSender): Int =
        s.toIntOrNull() ?: illegalArgument("无法解析 $s 为整数")
}

object LongArgParser : CommandArgParser<Long>() {
    override fun parse(s: String, sender: CommandSender): Long =
        s.toLongOrNull() ?: illegalArgument("无法解析 $s 为长整数")
}

object ShortArgParser : CommandArgParser<Short>() {
    override fun parse(s: String, sender: CommandSender): Short =
        s.toShortOrNull() ?: illegalArgument("无法解析 $s 为短整数")
}

object ByteArgParser : CommandArgParser<Byte>() {
    override fun parse(s: String, sender: CommandSender): Byte =
        s.toByteOrNull() ?: illegalArgument("无法解析 $s 为字节")
}

object DoubleArgParser : CommandArgParser<Double>() {
    override fun parse(s: String, sender: CommandSender): Double =
        s.toDoubleOrNull() ?: illegalArgument("无法解析 $s 为小数")
}

object FloatArgParser : CommandArgParser<Float>() {
    override fun parse(s: String, sender: CommandSender): Float =
        s.toFloatOrNull() ?: illegalArgument("无法解析 $s 为小数")
}

object StringArgParser : CommandArgParser<String>() {
    override fun parse(s: String, sender: CommandSender): String = s
}

object BooleanArgParser : CommandArgParser<Boolean>() {
    override fun parse(s: String, sender: CommandSender): Boolean = s.trim().let { str ->
        str.equals("true", ignoreCase = true)
                || str.equals("yes", ignoreCase = true)
                || str.equals("enabled", ignoreCase = true)
    }
}

/**
 * require a bot that already login in console
 * input: Bot UIN
 * output: Bot
 * errors: String->Int convert, Bot Not Exist
 */
object ExistBotArgParser : CommandArgParser<Bot>() {
    override fun parse(s: String, sender: CommandSender): Bot {
        val uin = try {
            s.toLong()
        } catch (e: Exception) {
            error("无法识别QQ UIN$s")
        }
        return try {
            Bot.getInstance(uin)
        } catch (e: NoSuchElementException) {
            error("无法找到Bot $uin")
        }
    }
}

object ExistFriendArgParser : CommandArgParser<Friend>() {
    //Bot.friend
    //friend
    //~ = self
    override fun parse(s: String, sender: CommandSender): Friend {
        if (s == "~") {
            if (sender !is BotAware) {
                illegalArgument("无法解析～作为默认")
            }
            val targetID = when (sender) {
                is GroupContactCommandSender -> sender.realSender.id
                is ContactCommandSender -> sender.contact.id
                else -> illegalArgument("无法解析～作为默认")
            }
            return try {
                sender.bot.friends[targetID]
            } catch (e: NoSuchElementException) {
                illegalArgument("无法解析～作为默认")
            }
        }
        if (sender is BotAware) {
            return try {
                sender.bot.friends[s.toLong()]
            } catch (e: NoSuchElementException) {
                error("无法找到" + s + "这个好友")
            } catch (e: NumberFormatException) {
                error("无法解析$s")
            }
        } else {
            s.split(".").let { args ->
                if (args.size != 2) {
                    illegalArgument("无法解析 $s, 格式应为 机器人账号.好友账号")
                }
                return try {
                    Bot.getInstance(args[0].toLong()).friends.getOrNull(
                        args[1].toLongOrNull() ?: illegalArgument("无法解析 $s 为好友")
                    ) ?: illegalArgument("无法找到好友 ${args[1]}")
                } catch (e: NoSuchElementException) {
                    illegalArgument("无法找到机器人账号 ${args[0]}")
                }
            }
        }
    }

    override fun parse(s: SingleMessage, sender: CommandSender): Friend {
        if (s is At) {
            assert(sender is GroupContactCommandSender)
            return (sender as BotAware).bot.friends.getOrNull(s.target) ?: illegalArgument("At的对象非Bot好友")
        } else {
            error("无法解析 $s 为好友")
        }
    }
}

object ExistGroupArgParser : CommandArgParser<Group>() {
    override fun parse(s: String, sender: CommandSender): Group {
        //by default
        if ((s == "" || s == "~") && sender is GroupContactCommandSender) {
            return sender.contact as Group
        }
        //from bot to group
        if (sender is BotAware) {
            val code = try {
                s.toLong()
            } catch (e: NoSuchElementException) {
                error("无法识别Group Code$s")
            }
            return try {
                sender.bot.getGroup(code)
            } catch (e: NoSuchElementException) {
                error("无法找到Group " + code + " from Bot " + sender.bot.id)
            }
        }
        //from console/other
        return with(s.split(".")) {
            if (this.size != 2) {
                error("请使用BotQQ号.群号 来表示Bot的一个群")
            }
            try {
                Bot.getInstance(this[0].toLong()).getGroup(this[1].toLong())
            } catch (e: NoSuchElementException) {
                error("无法找到" + this[0] + "的" + this[1] + "群")
            } catch (e: NumberFormatException) {
                error("无法识别群号或机器人UIN")
            }
        }
    }
}

object ExistMemberArgParser : CommandArgParser<Member>() {
    //后台: Bot.Group.Member[QQ/名片]
    //私聊: Group.Member[QQ/名片]
    //群内: Q号
    //群内: 名片
    override fun parse(s: String, sender: CommandSender): Member {
        if (sender !is BotAware) {
            with(s.split(".")) {
                checkArgument(this.size >= 3) {
                    "无法识别Member, 请使用Bot.Group.Member[QQ/名片]的格式"
                }

                val bot = try {
                    Bot.getInstance(this[0].toLong())
                } catch (e: NoSuchElementException) {
                    illegalArgument("无法找到Bot")
                } catch (e: NumberFormatException) {
                    illegalArgument("无法识别Bot")
                }

                val group = try {
                    bot.getGroup(this[1].toLong())
                } catch (e: NoSuchElementException) {
                    illegalArgument("无法找到Group")
                } catch (e: NumberFormatException) {
                    illegalArgument("无法识别Group")
                }

                val memberIndex = this.subList(2, this.size).joinToString(".")
                return group.members.getOrNull(memberIndex.toLong())
                    ?: group.fuzzySearchMember(memberIndex)
                    ?: error("无法找到成员$memberIndex")
            }
        } else {
            val bot = sender.bot
            if (sender is GroupContactCommandSender) {
                val group = sender.contact as Group
                return try {
                    group.members[s.toLong()]
                } catch (ignored: Exception) {
                    group.fuzzySearchMember(s) ?: illegalArgument("无法找到成员$s")
                }
            } else {
                with(s.split(".")) {
                    if (this.size < 2) {
                        illegalArgument("无法识别Member, 请使用Group.Member[QQ/名片]的格式")
                    }
                    val group = try {
                        bot.getGroup(this[0].toLong())
                    } catch (e: NoSuchElementException) {
                        illegalArgument("无法找到Group")
                    } catch (e: NumberFormatException) {
                        illegalArgument("无法识别Group")
                    }

                    val memberIndex = this.subList(1, this.size).joinToString(".")
                    return try {
                        group.members[memberIndex.toLong()]
                    } catch (ignored: Exception) {
                        group.fuzzySearchMember(memberIndex) ?: illegalArgument("无法找到成员$memberIndex")
                    }
                }
            }
        }
    }

    override fun parse(s: SingleMessage, sender: CommandSender): Member {
        return if (s is At) {
            checkArgument(sender is GroupContactCommandSender)
            (sender.contact as Group).members[s.target]
        } else {
            illegalArgument("无法识别Member" + s.content)
        }
    }
}


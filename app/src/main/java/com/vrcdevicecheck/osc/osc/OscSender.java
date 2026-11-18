package com.vrcdevicecheck.osc.osc;

import android.content.Context;

import com.vrcdevicecheck.osc.R;
import com.vrcdevicecheck.osc.util.LocaleHelper;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;

/**
 * UDP 发送器。VRChat 默认在 9000 端口接收外部应用的 OSC 报文。
 *
 * 注意：UDP 无连接，send 成功只代表报文交给了系统协议栈，不代表 VRChat 收到了。
 * 真正是否收到，要看 VRChat 动作菜单里的 OSC Debug 面板。
 *
 * 本类的所有方法都必须在子线程调用。
 */
public class OscSender {

    /** 发送结果：ok=true 时 bytes 为发出的字节数；ok=false 时 message 为可直接展示的失败原因 */
    public static final class Result {
        public final boolean ok;
        public final int bytes;
        public final String message;

        private Result(boolean ok, int bytes, String message) {
            this.ok = ok;
            this.bytes = bytes;
            this.message = message;
        }

        public static Result success(int bytes) {
            return new Result(true, bytes, null);
        }

        public static Result failure(String message) {
            return new Result(false, 0, message);
        }
    }

    private DatagramSocket socket;

    private synchronized DatagramSocket socket() throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new DatagramSocket();
        }
        return socket;
    }

    public Result send(Context context, String host, int port, byte[] packet) {
        if (host == null || host.trim().isEmpty()) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_no_ip));
        }
        if (port < 1 || port > 65535) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_bad_port, port));
        }
        try {
            InetAddress address = InetAddress.getByName(host.trim());
            DatagramSocket s = socket();
            DatagramPacket datagram = new DatagramPacket(packet, packet.length, address, port);
            s.send(datagram);
            return Result.success(packet.length);
        } catch (UnknownHostException e) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_unresolved_ip, host));
        } catch (PortUnreachableException e) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_unreachable, port));
        } catch (IOException e) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_network,
                    safeMessage(e)));
        } catch (SecurityException e) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_no_permission));
        } catch (Throwable t) {
            return Result.failure(LocaleHelper.str(context, R.string.send_err_exception,
                    t.getClass().getSimpleName()));
        }
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        return message == null || message.isEmpty() ? t.getClass().getSimpleName() : message;
    }

    public synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
                // 关闭失败无需处理
            }
            socket = null;
        }
    }
}

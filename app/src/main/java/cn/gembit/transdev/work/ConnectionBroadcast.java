package cn.gembit.transdev.work;

import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Random;

@SuppressWarnings("WeakerAccess")
public class ConnectionBroadcast {

    private final static int BUFFER_SIZE = 1024;

    private final static String ENCODING = Charset.defaultCharset().name();

    private final static InetAddress BROADCAST_ADDRESS;
    private final static int PREFERRED_PORT = 9706;

    private final static int PORT_COUNT = 8;

    private static boolean sSending = false;
    private static boolean sReceiving = false;

    static {
        InetAddress address;
        try {
            address = InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            address = null;
        }
        BROADCAST_ADDRESS = address;
    }

    public static void sendBroadcast(
            String username, String password, final OnSendingStatusChangedCallback callback) {
        if (sSending) {
            callback.onSendingStatusChanged(sSending = true, "已在广播");
            return;
        }

        DataOutputStream dos = null;
        byte[] data = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);
            dos.writeInt(ServerWrapper.getSingleton().getPort());
            dos.writeUTF(username);
            dos.writeUTF(password);
            dos.writeUTF(ENCODING);
            dos.writeUTF(Build.BRAND + " (" + Build.MODEL + ")");
            dos.writeInt(dos.size());
            dos.flush();
            data = baos.toByteArray();
        } catch (IOException e) {
            data = null;
        } finally {
            if (dos != null) {
                try {
                    dos.close();
                } catch (IOException e) {
                    data = null;
                }
            }
        }

        if (data == null) {
            callback.onSendingStatusChanged(sSending = false, "数据读取失败");
            return;
        }
        if (data.length > BUFFER_SIZE) {
            callback.onSendingStatusChanged(sSending = false, "用户名或密码太长");
            return;
        }
        callback.onSendingStatusChanged(sSending = true, "正在广播");

        final DatagramPacket[] packets = new DatagramPacket[PORT_COUNT];
        for (int i = 0; i < PORT_COUNT; i++) {
            packets[i] = new DatagramPacket(
                    data, data.length, BROADCAST_ADDRESS, PREFERRED_PORT + i);
        }
        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                try {
                    socket = new DatagramSocket();
                    socket.setBroadcast(true);
                    for (int i = 0; sSending; i++, i %= packets.length) {
                        socket.send(packets[i]);
                        SystemClock.sleep(100);
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSendingStatusChanged(sSending = false, "已关闭");
                        }
                    });
                } catch (IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSendingStatusChanged(sSending = false, "广播失败");
                        }
                    });
                } finally {
                    if (socket != null) {
                        socket.close();
                    }
                }

            }
        }).start();
    }

    public static void receiveBroadcast(final OnReceivedCallback callback) {
        if (sReceiving) {
            return;
        }
        sReceiving = true;
        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket socket = null;
                for (int i = 0; socket == null && i < PORT_COUNT; i++) {
                    try {
                        socket = new DatagramSocket(PREFERRED_PORT + i);
                    } catch (SocketException e) {
                        socket = null;
                    }
                }

                if (socket == null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            sReceiving = false;
                            callback.onReceived(null, "搜寻失败");
                        }
                    });
                    return;
                }


                final ClientAction.Argument.Connect argument = new ClientAction.Argument.Connect();
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.setSoTimeout(2500);

                    while (sReceiving) {
                        try {
                            socket.receive(packet);
                        } catch (SocketTimeoutException e) {
                            continue;
                        }

                        DataInputStream dis = new DataInputStream(
                                new ByteArrayInputStream(packet.getData(), 0, packet.getLength()));

                        argument.address = packet.getAddress().getHostAddress();

                        int received = dis.available();
                        argument.port = dis.readInt();
                        argument.username = dis.readUTF();
                        argument.password = dis.readUTF();
                        argument.encoding = dis.readUTF();
                        argument.alias = dis.readUTF();

                        if (received - dis.available() == dis.readInt() &&
                                !argument.address.equals(ServerWrapper.getSingleton().getIP())) {
                            break;
                        }
                        argument.address = null;
                    }

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            sReceiving = false;
                            callback.onReceived(
                                    argument.address != null ? argument : null,
                                    argument.address != null ? "搜寻结束" : "搜寻中止");
                        }
                    });

                } catch (IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            sReceiving = false;
                            callback.onReceived(null, "搜寻失败");
                        }
                    });
                } finally {
                    socket.close();
                }
            }
        }).start();
    }

    public static void stopSending() {
        sSending = false;
    }

    public static void stopReceiving() {
        sReceiving = false;
    }

    public static String tryOutMyIP() {
        final String preferredIPPrefix = "192.168.";
        final LinkedList<String> enumIP = new LinkedList<>();
        final String[] netIP = new String[1];

        Enumeration<NetworkInterface> en;
        try {
            en = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException ex) {
            en = null;
        }
        while (en != null && en.hasMoreElements()) {
            Enumeration<InetAddress> enumIPAddress = en.nextElement().getInetAddresses();
            while (enumIPAddress.hasMoreElements()) {
                InetAddress inetAddress = enumIPAddress.nextElement();
                if (inetAddress instanceof Inet4Address && inetAddress.isSiteLocalAddress()) {
                    if (inetAddress.getHostAddress().startsWith(preferredIPPrefix)) {
                        enumIP.add(0, inetAddress.getHostAddress());
                    } else {
                        enumIP.add(inetAddress.getHostAddress());
                    }
                }
            }
        }

        if (enumIP.size() == 1 || enumIP.size() > 1 &&
                enumIP.get(0).startsWith(preferredIPPrefix) &&
                !enumIP.get(1).startsWith(preferredIPPrefix)) {
            return enumIP.getFirst();
        } else if (enumIP.isEmpty()) {
            return null;
        }

        int testPort = PREFERRED_PORT;
        DatagramSocket receiveSocket = null;
        while (receiveSocket == null) {
            try {
                receiveSocket = new DatagramSocket(++testPort);
                final int receivingTimeout = 500;
                receiveSocket.setSoTimeout(receivingTimeout);
            } catch (BindException e1) {
                receiveSocket = null;
            } catch (SocketException e2) {
                receiveSocket = null;
                break;
            }
        }
        final int receivePort = testPort;

        final int checkDataLength = 8;

        final byte[] sentBytes = new byte[checkDataLength];
        new Random().nextBytes(sentBytes);
        final boolean[] sendError = {false};
        new Thread(new Runnable() {
            @Override
            public void run() {
                final DatagramPacket packet = new DatagramPacket(sentBytes, checkDataLength,
                        ConnectionBroadcast.BROADCAST_ADDRESS, receivePort);
                DatagramSocket sendSocket = null;
                try {
                    sendSocket = new DatagramSocket();
                    sendSocket.setBroadcast(true);
                    while (netIP[0] == null) {
                        sendSocket.send(packet);
                    }
                } catch (IOException e) {
                    sendError[0] = true;
                } finally {
                    if (sendSocket != null) {
                        sendSocket.close();
                    }
                }
            }
        }).start();

        final byte[] receivedBytes = new byte[checkDataLength];
        final DatagramPacket packet = new DatagramPacket(receivedBytes, checkDataLength);
        int receivingTryCount = 20;
        while (receiveSocket != null && !sendError[0] && receivingTryCount-- > 0) {
            try {
                receiveSocket.receive(packet);
                if (packet.getLength() == checkDataLength) {
                    boolean checked = true;
                    for (int i = 0; i < checkDataLength; i++) {
                        if (receivedBytes[i] != sentBytes[i]) {
                            checked = false;
                            break;
                        }
                    }
                    if (checked) {
                        netIP[0] = packet.getAddress().getHostAddress();
                        break;
                    }
                }
            } catch (SocketTimeoutException e1) {
                netIP[0] = null;
            } catch (IOException e2) {
                netIP[0] = null;
                break;
            }
        }

        if (receiveSocket != null) {
            receiveSocket.close();
        }

        if (netIP[0] != null) {
            return netIP[0];
        } else {
            String ip = "";
            for (int i = 0; i < enumIP.size(); i++) {
                ip += "\n" + enumIP.get(i);
            }
            return ip;
        }
    }

    public interface OnReceivedCallback {
        void onReceived(ClientAction.Argument.Connect argument, String brief);
    }

    public interface OnSendingStatusChangedCallback {
        void onSendingStatusChanged(boolean sending, String brief);
    }
}

package net.java.mproxy;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetSocketAddress;

public class WinRedirect {
    static {
        File dll = new File("win_redirect.dll");
        if (!dll.exists()) {
            throw new RuntimeException("win_redirect.dll not found");
        }
        System.load(dll.getAbsolutePath());
    }


    public enum Layer {
        NETWORK, NETWORK_FORWARD
    }

    public static long redirectStart(int targetPort, int localPort, Inet4Address[] srcAddresses, Inet4Address[] dstAddresses, Layer layer) {
        StringBuilder filter = new StringBuilder();
        filter.append("tcp");
        filter.append(" and (tcp.DstPort == ");
        filter.append(targetPort);
        filter.append(")");
        if (srcAddresses != null) {
            if (srcAddresses.length > 0) {
                filter.append(" and (");
            }
            for (int i = 0; i < srcAddresses.length; i++) {
                filter.append("ip.SrcAddr == ").append(srcAddresses[i].getHostAddress());
                if (i < srcAddresses.length - 1) {
                    filter.append(" or ");
                }
            }
            if (srcAddresses.length > 0) {
                filter.append(")");
            }
        }

        if (dstAddresses != null) {
            if (dstAddresses.length > 0) {
                filter.append(" and (");
            }
            for (int i = 0; i < dstAddresses.length; i++) {
                filter.append("ip.DstAddr == ").append(dstAddresses[i].getHostAddress());
                if (i < dstAddresses.length - 1) {
                    filter.append(" or ");
                }
            }
            if (dstAddresses.length > 0) {
                filter.append(")");
            }
        }
        System.out.println(filter);
        return redirectStart(localPort, filter.toString(), layer.ordinal());
    }

    protected static native long redirectStart(int redirect_port, String filter, int layer);

    public static native void redirectStop(long redirect);

    public static native void redirectPause(long redirect);

    public static native void redirectResume(long redirect);

    public static native boolean redirectAddSkipPort(long redirect, int port);

    public static native boolean redirectRemoveSkipPort(long redirect, int port);

    public static native int redirectGetActiveConnectionsCount(long redirect);

    public static native boolean redirectGetRealAddresses(long redirect, String ip, int port, InetSocketAddress[] out);

    public static native String getError();

    public static native long mdnsDisable(String ip);

    public static native boolean mdnsRestore(long mdns);

    public static native boolean enableTtlFix();

    public static native boolean disableTtlFix();

    //example: portForwardStart("192.168.137.1", "192.168.137.2", "192.168.137.254", "192.168.0.1", "192.168.0.2", 80);
    //192.168.0.2 - this pc ip address from which there is access to "toIp"
    public static native long portForwardStart(String listenIp, String listenStartIp, String listenEndIp, String toIp, String thisIp, int... skipPorts);

    public static native boolean portForwardStop(long portForward);

    public static native long blockIpStart(String startSrcIp, String endSrcIp, String startDstIp, String endDstIp);

    public static native boolean blockIpStop(long blockIp);
}
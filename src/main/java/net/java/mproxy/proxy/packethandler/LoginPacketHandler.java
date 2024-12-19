package net.java.mproxy.proxy.packethandler;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.java.mproxy.Proxy;
import net.java.mproxy.proxy.LoginState;
import net.java.mproxy.proxy.auth.AuthLibServices;
import net.java.mproxy.proxy.auth.ExternalInterface;
import net.java.mproxy.proxy.session.ProxyConnection;
import net.java.mproxy.proxy.util.ChannelUtil;
import net.java.mproxy.proxy.util.CloseAndReturn;
import net.java.mproxy.util.logging.Logger;
import net.raphimc.netminecraft.netty.crypto.AESEncryption;
import net.raphimc.netminecraft.netty.crypto.CryptUtil;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginHelloPacket;
import net.raphimc.netminecraft.packet.impl.login.C2SLoginKeyPacket;
import net.raphimc.netminecraft.packet.impl.login.S2CLoginHelloPacket;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class LoginPacketHandler extends PacketHandler {

    private static final KeyPair KEY_PAIR = CryptUtil.generateKeyPair();
    private static final Random RANDOM = new Random();

    private static final byte[] verifyToken = new byte[4];

    static {
        RANDOM.nextBytes(verifyToken);
    }

    private LoginState loginState = LoginState.FIRST_PACKET;

    public LoginPacketHandler(ProxyConnection proxyConnection) {
        super(proxyConnection);
    }

    @Override
    public boolean handleC2P(Packet packet, List<ChannelFutureListener> listeners) throws GeneralSecurityException {
        if (packet instanceof C2SLoginHelloPacket loginHelloPacket) {

            if (this.loginState != LoginState.FIRST_PACKET) throw CloseAndReturn.INSTANCE;
            this.loginState = LoginState.SENT_HELLO;

            if (loginHelloPacket.expiresAt != null && loginHelloPacket.expiresAt.isBefore(Instant.now())) {
                throw new IllegalStateException("Expired public key");
            }

            proxyConnection.setLoginHelloPacket(loginHelloPacket);
            if (loginHelloPacket.uuid != null) {
                proxyConnection.setGameProfile(new GameProfile(loginHelloPacket.uuid, loginHelloPacket.name));
            } else {
                proxyConnection.setGameProfile(new GameProfile(null, loginHelloPacket.name));
            }

            if (Proxy.getConfig().onlineMode.get()) {
//                if (proxyConnection.isController()) {
//                    Logger.raw("controller cancel and send response S2CLoginHelloPacket\n");
//                } else {
//                    Logger.raw("      side cancel and send response S2CLoginHelloPacket\n");
//                }
                this.proxyConnection.sendToClient(new S2CLoginHelloPacket("", KEY_PAIR.getPublic().getEncoded(), verifyToken, true), ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);


            } else {

                ExternalInterface.fillPlayerData(this.proxyConnection);
                this.proxyConnection.sendToServer(this.proxyConnection.getLoginHelloPacket(), ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
            if (!Proxy.getConfig().onlineMode.get()) {
                if (!proxyConnection.isController()) {
                    //was disabled in Client2ProxyHandler.connect
                    ChannelUtil.restoreAutoRead(proxyConnection.getChannel());
//                    synchronized (proxyConnection.dualConnection.waiter) {
//                        proxyConnection.dualConnection.waiter.notifyAll();
//                    }
                }
            }
            return false;
        } else if (packet instanceof C2SLoginKeyPacket loginKeyPacket) {
            if (this.loginState != LoginState.SENT_HELLO) throw CloseAndReturn.INSTANCE;
            this.loginState = LoginState.SENT_KEY;

            if (loginKeyPacket.encryptedNonce != null) {
                if (!Arrays.equals(verifyToken, CryptUtil.decryptData(KEY_PAIR.getPrivate(), loginKeyPacket.encryptedNonce))) {
                    Logger.u_err("auth", this.proxyConnection, "Invalid verify token");
                    this.proxyConnection.kickClient("Invalid verify token!");
                }
            } else {
                final C2SLoginHelloPacket loginHelloPacket = this.proxyConnection.getLoginHelloPacket();
                if (loginHelloPacket.key == null || !CryptUtil.verifySignedNonce(loginHelloPacket.key, verifyToken, loginKeyPacket.salt, loginKeyPacket.signature)) {
                    Logger.u_err("auth", this.proxyConnection, "Invalid verify token");
                    this.proxyConnection.kickClient("Invalid verify token!");
                }
            }

            final SecretKey secretKey = CryptUtil.decryptSecretKey(KEY_PAIR.getPrivate(), loginKeyPacket.encryptedSecretKey);
            this.proxyConnection.setC2pEncryption(new AESEncryption(secretKey));
            final String userName = this.proxyConnection.getGameProfile().getName();
            try {
                final String serverHash = new BigInteger(CryptUtil.computeServerIdHash("", KEY_PAIR.getPublic(), secretKey)).toString(16);
                final GameProfile mojangProfile = AuthLibServices.SESSION_SERVICE.hasJoinedServer(this.proxyConnection.getGameProfile(), serverHash, null);
                if (mojangProfile == null) {
                    Logger.u_err("auth", this.proxyConnection, "Invalid session");
                    this.proxyConnection.kickClient("Invalid session! Please restart minecraft (and the launcher) and try again.");
                } else {
                    this.proxyConnection.setGameProfile(mojangProfile);
                }
                Logger.u_info("auth", this.proxyConnection, "Authenticated as " + this.proxyConnection.getGameProfile().getId().toString());
            } catch (Throwable e) {
                throw new RuntimeException("Failed to make session request for user '" + userName + "'!", e);
            }
//            if (proxyConnection.isController()) {
//                Logger.raw("controller cancel and send C2SLoginHelloPacket instead\n");
//            } else {
//                Logger.raw("      side cancel and send C2SLoginHelloPacket instead\n");
//            }
            ExternalInterface.fillPlayerData(this.proxyConnection);


            this.proxyConnection.sendToServer(this.proxyConnection.getLoginHelloPacket(), ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            if (Proxy.getConfig().onlineMode.get()) {
                if (!proxyConnection.isController()) {
                    //was disabled in Client2ProxyHandler.connect
                    ChannelUtil.restoreAutoRead(proxyConnection.getChannel());
//                    synchronized (proxyConnection.dualConnection.waiter) {
//                        proxyConnection.dualConnection.waiter.notifyAll();
//                    }
                }
            }
            return false;
        }

        return true;
    }


}

package com.nix.pack.obtain;
import com.nix.note.data.Note;
import com.nix.pack.ObtainPackage;
import com.nix.pack.process.ClientJpcapHttpPackageProcess;
import jpcap.JpcapCaptor;
import jpcap.NetworkInterface;
import jpcap.PacketReceiver;
import jpcap.packet.IPPacket;
import jpcap.packet.Packet;
import jpcap.packet.TCPPacket;

import java.util.concurrent.*;

/**
 * @author 11723
 * 抓包工作类
 */
public class ClientJpcapObtain extends JpcapObtainPackage {


    private static ClientJpcapObtain obtainPackage = null;

    /**
     * 单例模式
     * */
    public static ObtainPackage getObtainPackage(NetworkInterface networkInterface) {
        if (obtainPackage == null) {
            synchronized (clock) {
                if (obtainPackage == null) {
                    obtainPackage = new ClientJpcapObtain(networkInterface);
                }
            }
        }
        return obtainPackage;
    }

    private static Note note = new Note("192.168.0.100","c8:d3:ff:d3:a5:ac");

    private ClientJpcapObtain(NetworkInterface networkInterface){
        super(networkInterface);
        this.process = new ClientJpcapHttpPackageProcess(networkInterface,note);
    }

    /**
     * 数据包处理器
     * */
    private final ClientJpcapHttpPackageProcess process;

    /**
     * 工作状态
     * */
    private boolean status = false;

    private JpcapCaptor captor = null;

    /**
     * 开始抓包
     * */
    @Override
    public void start() {
        if (status) {
            return;
        }
        status = true;
        threadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // 设置只抓取tcp端口的数据包
                    captor = JpcapCaptor.openDevice(networkInterface, 65535, true, 20);
                    captor.setFilter("tcp",true);
                    captor.loopPacket(-1, new PacketReceiver() {
                        @Override
                        public void receivePacket(Packet packet) {
                            if (((IPPacket)packet).src_ip.equals(networkInterface.addresses[1].address)) {
                                if (packet instanceof TCPPacket && ((TCPPacket) packet).src_port == 80) {
                                    // 处理数据包放到另外的工作线程处理
                                    threadPool.execute(new Runnable() {
                                        @Override
                                        public void run() {
                                            process.addHttpPackage((TCPPacket)packet);
                                        }
                                    });
                                }
                            }

                        }
                    });
                } catch (Exception e) {
                    System.out.println("执行失败");
                    e.printStackTrace();
                }
            }
        });
    }
}

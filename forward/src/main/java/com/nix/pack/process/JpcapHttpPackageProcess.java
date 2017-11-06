package com.nix.pack.process;

import com.nix.note.data.supper.MemoryNoteCache;
import com.nix.note.data.Note;
import com.nix.note.data.NoteCache;
import com.nix.pack.Process;
import com.nix.pack.obtain.JpcapObtainPackage;
import jpcap.JpcapCaptor;
import jpcap.JpcapSender;
import jpcap.NetworkInterface;
import jpcap.packet.EthernetPacket;
import jpcap.packet.IPPacket;
import jpcap.packet.Packet;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * @author 11723
 * 加工jpcap抓取到的http数据包
 */
public class JpcapHttpPackageProcess implements Process<IPPacket>{

    /**
     * 数据包发送网卡句柄
     * */
    private final JpcapSender sender;

    public JpcapHttpPackageProcess(NetworkInterface networkInterface) {
        JpcapSender j = null;
        try {
            j = JpcapSender.openDevice(networkInterface);
        } catch (IOException e) {
            e.printStackTrace();
        }
        sender = j;
    }

    /**
     * 节点缓存引用
     * */
    private NoteCache noteCache = MemoryNoteCache.getNoteCache();


    /**
     * 对分片ip包的缓存
     * */
    private ConcurrentHashMap<String,List<IPPacket>> packetCache = new ConcurrentHashMap<>();
    /**
     * 保存分片数据包缓存时间 便于清除超时还没传送完整的缓存
     * */
    private ConcurrentHashMap<String,Long> packetCacheTime = new ConcurrentHashMap<>();

    /**
     * 抓包线程{@link JpcapObtainPackage}获取到一个http数据包交给此方法处理
     * */
    public void addHttpPackage(IPPacket packet) {
        try {
            // 当ip数据包为不分片的单包时直接转发到节点
            if (!packet.more_frag && packet.offset == 0) {
                distributionPackToNote(packet);
            }else {
                if (packetCache.containsKey(String.valueOf(packet.ident))) {
                    packetCache.get(String.valueOf(packet.ident)).add(packet);
                    process(packetCache.get(String.valueOf(packet.ident)));
                }else {
                    /**
                     * 分组数据包第一次被获取 添加到packet缓存中
                     * Collections.synchronizedList(new ArrayList<IPPacket>()) 保证list操作线程安全
                     */
                    List<IPPacket> packets = Collections.synchronizedList(new ArrayList<IPPacket>());
                    packets.add(packet);
                    //添加一个分组的缓存
                    packetCache.put(String.valueOf(packet.ident),packets);
                    //保存缓存创建时间
                    packetCacheTime.put(String.valueOf(packet.ident),System.nanoTime());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void process(List<IPPacket> packetList){
        IPPacket[] packets = new IPPacket[packetList.size()];
        packetList.toArray(packets);
        //对同一标志的ip数据包检查接收是否完整
        //需要重新优化不需要排序完成
        for(int i = 1;i < packets.length; i ++){
            IPPacket k = packets[i];
            int j = i;
            for (;j > 0 && packets[j - 1].offset > k.offset;j--) {
                packets[j] = packets[j - 1];
            }
            packets[j] = k;
        }
        //如果接收完整
        if (!packets[packets.length - 1].more_frag) {
            distributionPackToNote(packets);
        }

    }

    @Override
    public void distributionPackToNote(IPPacket ... packets) {
        try {
            Note note = noteCache.getExcellentNote();
            for (IPPacket packet:packets) {
                //更改ip数据包的目的ip地址
                packet.dst_ip = Inet4Address.getByName(note.getIp());
                //更改ip数据包的目的mac地址
                ((EthernetPacket) packet.datalink).dst_mac = note.getByteMac();

                sender.sendPacket(packet);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
package com.nix.pack;

import com.nix.note.data.Note;

/**
 * @author 11723
 */
public interface Process<P extends Object> {

    /**
     * 分发数据包到负载节点
     * @param o 处理的数据包组
     * */
    void distributionPackToNote(Note note, P ... o);


}

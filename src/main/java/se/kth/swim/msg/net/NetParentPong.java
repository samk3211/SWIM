/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.kth.swim.msg.net;

import se.kth.swim.msg.ParentPing;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 *
 * @author fabriziodemaria
 */
public class NetParentPong extends NetMsg<ParentPing>  {

   public NetParentPong(NatedAddress src, NatedAddress dst) {
        super(src, dst, new ParentPing());
    }


    private NetParentPong(Header<NatedAddress> header, ParentPing content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetParentPong(newHeader, getContent());
    }


}
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.kth.swim.msg.net;

import se.kth.swim.msg.DeadNotification;
import se.kth.swim.msg.Ping;
import se.sics.kompics.network.Header;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 *
 * @author fabriziodemaria
 */
public class NetDeadNotification extends NetMsg<DeadNotification> {

    public NetDeadNotification(NatedAddress src, NatedAddress dst, NatedAddress deadNode) {
        super(src, dst, new DeadNotification(deadNode));
    }

    private NetDeadNotification(Header<NatedAddress> header, DeadNotification content) {
        super(header, content);
    }

    @Override
    public NetMsg copyMessage(Header<NatedAddress> newHeader) {
        return new NetDeadNotification(newHeader, getContent());
    }

}

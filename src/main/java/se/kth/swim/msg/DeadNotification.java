/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package se.kth.swim.msg;

import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 *
 * @author fabriziodemaria
 */
public class DeadNotification {
    
    private NatedAddress deadNode;
    
    public DeadNotification(NatedAddress na){
        deadNode=na;
    }

    /**
     * @return the deadNode
     */
    public NatedAddress getDeadNode() {
        return deadNode;
    }

    /**
     * @param deadNode the deadNode to set
     */
    public void setDeadNode(NatedAddress deadNode) {
        this.deadNode = deadNode;
    }
    
}

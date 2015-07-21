/*
* Copyright (C) 2009 Swedish Institute of Computer Science (SICS) Copyright (C)
* 2009 Royal Institute of Technology (KTH)
*
* GVoD is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
*/
package se.kth.swim;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.croupier.CroupierPort;
import se.kth.swim.croupier.msg.CroupierSample;
import se.kth.swim.croupier.util.Container;
import se.kth.swim.msg.DeadNotification;
import se.kth.swim.msg.ParentPing;
import se.kth.swim.msg.Ping;
import se.kth.swim.msg.net.NetDeadParent;
import se.kth.swim.msg.net.NetMsg;
import se.kth.swim.msg.net.NetNewParent;
import se.kth.swim.msg.net.NetParentPing;
import se.kth.swim.msg.net.NetPing;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Negative;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Header;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;
import se.sics.p2ptoolbox.util.network.impl.RelayHeader;
import se.sics.p2ptoolbox.util.network.impl.SourceHeader;

/**
 *
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class NatTraversalComp extends ComponentDefinition {
    
    private static final int MAX_NUMBER_PARENTS = 5;
    private static final int PERIODIC_PARENT_PING = 1000;
    private static final int PARENT_ACK_TIMER = 1000;
    
    private UUID parentPingTimeoutId;
    private HashMap<Integer,UUID> parentAckTimeoutIds = new HashMap<Integer,UUID>();
    
    private static final Logger log = LoggerFactory.getLogger(NatTraversalComp.class);
    private Negative<Network> local = provides(Network.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<CroupierPort> croupier = requires(CroupierPort.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private NatedAddress selfAddress;
    private final Random rand;
    
    private HashSet<Integer> tabuList = new HashSet<Integer>();
    
    public NatTraversalComp(NatTraversalInit init) {
        this.selfAddress = init.selfAddress;
        //  log.info("{} {} initiating...", new Object[]{selfAddress.getId(), (selfAddress.isOpen() ? "OPEN" : "NATED")});
        
        this.rand = new Random(init.seed);
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleIncomingMsg, network);
        subscribe(handleOutgoingMsg, local);
        subscribe(handleCroupierSample, croupier);
        subscribe(handleParentPingTimeout, timer);
        subscribe(handleParentAckTimeout, timer);
    }
    
    private Handler<Start> handleStart = new Handler<Start>() {
        
        @Override
        public void handle(Start event) {
            //    log.info("{} starting...", new Object[]{selfAddress.getId()});
            
            log.info(" {} myparents init: {}", new Object[]{selfAddress.getId(), selfAddress.getParents()});
            for(NatedAddress na : selfAddress.getParents()){
                tabuList.add(na.getId());
            }
            if(!selfAddress.isOpen())
                schedulePeriodicPing();
        }
        
    };
    
    private Handler<Stop> handleStop = new Handler<Stop>() {
        
        @Override
        public void handle(Stop event) {
            //   log.info("{} stopping...", new Object[]{selfAddress.getId()});
        }
        
    };
    
    private final Handler<ParentPingTimeout> handleParentPingTimeout = new Handler<ParentPingTimeout>() {
        
        @Override
        public void handle(ParentPingTimeout event) {
            
            for(NatedAddress na : selfAddress.getParents()){
                if(!parentAckTimeoutIds.containsKey(na.getId())){
                    scheduleParentAck(na);
          //          log.info("PPARENT - {} pinging parent {} ", new Object[]{selfAddress.getId(), na.getId()});
                    trigger(new NetParentPing(selfAddress,na), network);
                }
            }
        }
    };
    
    private final Handler<ParentAckTimeout> handleParentAckTimeout = new Handler<ParentAckTimeout>() {
        
        @Override
        public void handle(ParentAckTimeout event) {
            cancelParentAck(event.getAddress());
   //          log.info("PPARENT - {} didn't receive pong from {} ", new Object[]{selfAddress.getId(), event.getAddress().getId()});
            NatedAddress toRemove = null;
                for(NatedAddress na : selfAddress.getParents()){
                    if(na.getId().equals(event.getAddress().getId())){
                        toRemove = na;
                    }
                }
                if(toRemove!=null){
                    selfAddress = deepCopyRemove(selfAddress,toRemove);
                    log.info(" {} myparents removed: {}", new Object[]{selfAddress.getId(), selfAddress.getParents()});
                    trigger(new NetDeadParent(selfAddress,selfAddress,toRemove),local);
                }
            
        }
    };
    
    
    
    private Handler<CroupierSample> handleCroupierSample = new Handler<CroupierSample>() {
        @Override
        public void handle(CroupierSample event) {
            log.info("{} croupier public nodes:{}", selfAddress.getBaseAdr(), event.publicSample);
            //use this to change parent in case it died
            
            if(!selfAddress.isOpen() && selfAddress.getParents().size()<MAX_NUMBER_PARENTS){
                for(Object c : event.publicSample){
                    Container tempContainer = (Container)c;
                    NatedAddress na = (NatedAddress)tempContainer.getSource();
                    
                    //Check if I alread have hte parent
                    if(parentsContain(selfAddress.getParents(),na)) continue;
                    
                    if(na.isOpen() && na.getId()!=selfAddress.getId() && !tabuList.contains(na.getId()) && selfAddress.getParents().size()<MAX_NUMBER_PARENTS){
                        //Update parents list
                        log.info("{} adding {} to my parent list", new Object[]{selfAddress.getId(), na.getId()});
                        tabuList.add(na.getId());
                        selfAddress = deepCopy(selfAddress,na);
                        log.info(" {} myparents added: {}", new Object[]{selfAddress.getId(), selfAddress.getParents()});
                        NetNewParent nnp = new NetNewParent(selfAddress,selfAddress, na);
                        trigger(nnp,local);
                    }
                }
            }
            
        }
    };
    
    
    
    private Handler<NetMsg<Object>> handleIncomingMsg = new Handler<NetMsg<Object>>() {
        
        @Override
        public void handle(NetMsg<Object> msg) {
            
            if(msg.getContent() instanceof ParentPing && selfAddress.isOpen()){
                //Handling ping from nated child
           //      log.info("PPARENT - {} received ping from child {} ", new Object[]{selfAddress.getId(), msg.getSource().getId()});
                trigger(new NetParentPing(selfAddress,msg.getSource()), network);
            } else if (msg.getContent() instanceof ParentPing && !selfAddress.isOpen()){
                //Handling pong from parent
               //  log.info("PPARENT - {} receivd pong from parent {} ", new Object[]{selfAddress.getId(), msg.getSource().getId()});
                cancelParentAck(msg.getSource());
            }
            
            
            
            log.trace("{} nnnn received msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if (header instanceof SourceHeader) {
                if (!selfAddress.isOpen()) {
                    throw new RuntimeException("source header msg received on nated node - nat traversal logic error");
                }
                SourceHeader<NatedAddress> sourceHeader = (SourceHeader<NatedAddress>) header;
                if (parentsContain(sourceHeader.getActualDestination().getParents(),selfAddress)) {
                    log.info("{} nnnn relaying message for:{}", new Object[]{selfAddress.getId(), sourceHeader.getSource()});
                    RelayHeader<NatedAddress> relayHeader = sourceHeader.getRelayHeader();
                    trigger(msg.copyMessage(relayHeader), network);
                    return;
                } else {
                    log.warn("{} received weird relay message:{} - dropping it", new Object[]{selfAddress.getId(), msg});
                    return;
                }
            } else if (header instanceof RelayHeader) {
                if (selfAddress.isOpen()) {
                    throw new RuntimeException("relay header msg received on open node - nat traversal logic error");
                }
                RelayHeader<NatedAddress> relayHeader = (RelayHeader<NatedAddress>) header;
                log.info("{} nnnn delivering relayed message:{} from:{} - The relay was {}", new Object[]{selfAddress.getId(), msg, relayHeader.getActualSource(), relayHeader.getSource()});
                Header<NatedAddress> originalHeader = relayHeader.getActualHeader();
                trigger(msg.copyMessage(originalHeader), local);
                return;
            } else {
                if(msg.getContent() instanceof Ping)
                    log.info("{} nnnn delivering direct message:{} from:{}", new Object[]{selfAddress.getId(), msg, header.getSource()});
                trigger(msg, local);
                return;
            }
        }
        
    };
    
    private Handler<NetMsg<Object>> handleOutgoingMsg = new Handler<NetMsg<Object>>() {
        
        @Override
        public void handle(NetMsg<Object> msg) {
            
            //First check if it is a DEAD NODE notification
            if(msg.getContent() instanceof DeadNotification && !selfAddress.isOpen()){
                /*
                DeadNotification dn = (DeadNotification)msg.getContent();
                log.info(" {} deadnotification for: {} - current parent list: {}", new Object[]{selfAddress.getId(), dn.getDeadNode().getId(), selfAddress.getParents()});
                NatedAddress toRemove = null;
                for(NatedAddress na : selfAddress.getParents()){
                    if(na.getId().equals(dn.getDeadNode().getId())){
                        toRemove = na;
                    }
                }
                if(toRemove!=null){
                    selfAddress = deepCopyRemove(selfAddress,toRemove);
                    log.info(" {} myparents removed: {}", new Object[]{selfAddress.getId(), selfAddress.getParents()});
                    
                    trigger(new NetDeadParent(selfAddress,selfAddress,toRemove),local);
                }
                return;
                        */
            }
            
            log.info("{} nnnn sending msg:{}", new Object[]{selfAddress.getId(), msg});
            Header<NatedAddress> header = msg.getHeader();
            if(header.getDestination().isOpen()) {
                log.info("{} nnnn sending direct message:{} to:{}", new Object[]{selfAddress.getId(), msg, header.getDestination()});
                trigger(msg, network);
                return;
            } else {
                if(header.getDestination().getParents().isEmpty()) {
                    //throw new RuntimeException("nated node with no parents");
                    return;
                }
                NatedAddress parent = randomNode(header.getDestination().getParents());
                SourceHeader<NatedAddress> sourceHeader = new SourceHeader(header, deepCopy(parent));
                log.info("{} nnnn sending message:{} to relay:{} - real destination {}", new Object[]{selfAddress.getId(), msg, parent, msg.getHeader().getDestination()});
                trigger(msg.copyMessage(sourceHeader), network);
                return;
            }
        }
        
    };
    
    private NatedAddress randomNode(Set<NatedAddress> nodes) {
        int index = rand.nextInt(nodes.size());
        Iterator<NatedAddress> it = nodes.iterator();
        while(index > 0) {
            it.next();
            index--;
        }
        return it.next();
    }
    
    private boolean parentsContain(Set<NatedAddress> parents, NatedAddress na){
        HashSet<Integer> parentIds = new HashSet<Integer>();
        
        Object[] nodes = parents.toArray();
        
        for(Object o : nodes){
            NatedAddress tmp = (NatedAddress)o;
            parentIds.add(tmp.getId());
        }
        return parentIds.contains(na.getId());
    }
    
    private NatedAddress deepCopy(NatedAddress self, NatedAddress par){
        Set<NatedAddress> parentListTmp = new HashSet<NatedAddress>(self.getParents());
        parentListTmp.add(par);
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(self.getIp(), self.getPort(), self.getId()), self.getNatType(), parentListTmp);
        return na2;
    }
    
    private NatedAddress deepCopy(NatedAddress na){
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(na.getIp(), na.getPort(), na.getId()), na.getNatType(), na.getParents());
        return na2;
    }
    
    private NatedAddress deepCopyRemove(NatedAddress self, NatedAddress par){
        Set<NatedAddress> parentListTmp = new HashSet<NatedAddress>(self.getParents());
        parentListTmp.remove(par);
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(self.getIp(), self.getPort(), self.getId()), self.getNatType(), parentListTmp);
        return na2;
    }
    
    
    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(PERIODIC_PARENT_PING, PERIODIC_PARENT_PING);
        ParentPingTimeout sc = new ParentPingTimeout(spt);
        spt.setTimeoutEvent(sc);
        parentPingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(parentPingTimeoutId);
        trigger(cpt, timer);
        parentPingTimeoutId = null;
    }
    
    private void scheduleParentAck(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(PARENT_ACK_TIMER);
        ParentAckTimeout sc = new ParentAckTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        parentAckTimeoutIds.put(address.getId(),sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelParentAck(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(parentAckTimeoutIds.get(address.getId()));
        trigger(cpt, timer);
        parentAckTimeoutIds.remove(address.getId());
    }
    
    
    public static class NatTraversalInit extends Init<NatTraversalComp> {
        
        public final NatedAddress selfAddress;
        public final long seed;
        
        public NatTraversalInit(NatedAddress selfAddress, long seed) {
            this.selfAddress = selfAddress;
            this.seed = seed;
        }
    }
    
     private static class ParentPingTimeout extends Timeout {
        
        public ParentPingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
     
     private static class ParentAckTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public ParentAckTimeout(ScheduleTimeout request, NatedAddress address) {
            super(request);
            this.address = address;
        }
        
        /**
         * @return the address
         */
        public NatedAddress getAddress() {
            return address;
        }
    }
    
}

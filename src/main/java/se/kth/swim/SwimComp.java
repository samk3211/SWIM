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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.Status;
import se.kth.swim.msg.net.InfoPiggyback;
import se.kth.swim.msg.net.InfoType;
import se.kth.swim.msg.net.NetDeadParent;
import se.kth.swim.msg.net.NetNewParent;
import se.kth.swim.msg.net.NetPing;
import se.kth.swim.msg.net.NetPingReq;
import se.kth.swim.msg.net.NetPingResp;
import se.kth.swim.msg.net.NetPong;
import se.kth.swim.msg.net.NetStatus;
import se.kth.swim.simulation.SwimMain;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.CancelTimeout;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.ScheduleTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicAddress;
import se.sics.p2ptoolbox.util.network.impl.BasicNatedAddress;


public class SwimComp extends ComponentDefinition {
    
    
    
    
    /*
    
    CONFIGURATION PANEL
    
    */
    
    
    
    //PRINTING OPTIONS
    public static final boolean printListOfNeighbours = false;
    public static final boolean debugPB = false;
    
    
    
    //PROTOCOL TUNING
    private static final Integer DISSEMINATION_VALUE = SwimMain.disseminationValue;
    private static final Integer K_VALUE = SwimMain.kValue;
    private static final Integer MAX_LIST_SIZE = SwimMain.pbSize;
    
    
    
    //TIMERS
    private static final int PERIODIC_PING = 1000;
    private static final int PERIODIC_STATUS = 1000;
    //the next two can be almost the same...
    private static final int ACK_TIMER = 10000;
    public static final int DELETE_REQ_TIMER = 10000;
    private static final int DEAD_TIMER = 100000; //usually bigger than previos two... Keep it SUSPECTED for a while
    //Has to be higher than delete_req_timer
    private static final int INDIRECT_PING_TIMER = 20000; //x2 with respect to DELETE_REQ_TIMER, just in case
    
    
    
    
    
    
    private static final Logger log = LoggerFactory.getLogger(SwimComp.class);
    
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private  NatedAddress selfAddress;
    private final MembershipList membershipList;
    private final NatedAddress aggregatorAddress;
    
    
    private final PiggybackList pbList;
    private HashMap<Integer, Integer> requestMapping = new HashMap<Integer,Integer>(); //Ping final destination - Initiali requestor
    
    private UUID pingTimeoutId;
    private UUID statusTimeoutId;
    private HashMap<Integer,UUID> ackTimeoutIds = new HashMap<Integer,UUID>();
    private HashMap<Integer,UUID> deleteTimeoutIds = new HashMap<Integer,UUID>();
    private HashMap<Integer,UUID> indTimeoutIds = new HashMap<Integer,UUID>();  
    private HashMap<Integer,UUID> deadTimeoutIds = new HashMap<Integer,UUID>();
    private HashMap<Integer,NatedAddress> requestorsIndirect = new HashMap<Integer,NatedAddress>();
    
    public SwimComp(SwimInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", selfAddress);
        this.membershipList = new MembershipList(init.bootstrapNodes,selfAddress);
        this.aggregatorAddress = init.aggregatorAddress;
        this.pbList = new PiggybackList(MAX_LIST_SIZE);
        

        for(NatedAddress na : membershipList.getNeighboursList()){
            log.info("{} starting peers: {}", selfAddress, na.getId());
        }
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handlePing, network);
        subscribe(handlePong, network);
        subscribe(handlePingTimeout, timer);
        subscribe(handleStatusTimeout, timer);
        subscribe(handleAckTimeout, timer);
        subscribe(handleDeadTimeout, timer);
        subscribe(handlePingReqMsg, network);
        subscribe(handleDeleteTimeout, timer);
        subscribe(handlePingRespMsg, network);
        subscribe(handleIndTimeout, timer);
        subscribe(handleNewParentMesg,network);
        subscribe(handleDeadParentMesg,network);
        
    }
    
    private Handler<Start> handleStart = new Handler<Start>() {
        
        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress.getId()});
            
            if (!membershipList.isEmpty()) {
                schedulePeriodicPing();
            }
            schedulePeriodicStatus();
        }
    };
    
    private Handler<Stop> handleStop = new Handler<Stop>() {
        
        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress.getId()});
            if (pingTimeoutId != null) {
                cancelPeriodicPing();
            }
            if (statusTimeoutId != null) {
                cancelPeriodicStatus();
            }
        }
        
    };
    
    
    
    
    /*
    *
    *   P I N G received
    *
    */
    
    private Handler<NetPing> handlePing = new Handler<NetPing>() {
        
        @Override
        public void handle(NetPing event) {
            
            Integer pingInc= event.getContent().incarnationValue;
            printmyneigh(); //method activated only if debug flag is ON
            
//Handle indirect-ping protocol
//If you received a ping from a node that you are testing via indirect procedure, cancel timeout
            if(indTimeoutIds.keySet().contains(event.getHeader().getSource().getId())){
                cancelIndTimeout(event.getHeader().getSource());
                membershipList.unsuspectNode(event.getHeader().getSource(),pingInc);
            }
            
//Ping from unknown node
            if(!membershipList.contains(event.getHeader().getSource())){
                
//Maybe it is the first node in the list -> start pingTimeout
                if (pingTimeoutId == null) {
                    schedulePeriodicPing();
                }
                
//Save the new info to be disseminated and add it to the list
                if(membershipList.add(event.getHeader().getSource(),pingInc))
                    pbList.add(new InfoPiggyback(InfoType.NEWNODE,event.getHeader().getSource(),pingInc), DISSEMINATION_VALUE);
                
                /*
                Catch-up procedure: send a DEAD info to the node with current incarnation value to let it get the
                highest value it had before crashing (if it enters for the first time the value will be 0)
                */
//CAREFUL! TESTING ONLY
                //ArrayList<InfoPiggyback> catchup = new ArrayList<InfoPiggyback>();
                //catchup.add(new InfoPiggyback(InfoType.DEADNODE,event.getHeader().getSource(),membershipList.getIncarnationForMember(event.getHeader().getSource())));
                //trigger(new NetPong(deepCopy(selfAddress), event.getHeader().getSource(), catchup, membershipList.getSelfIncarnation()), network);
                
            } else {
                
//Ping is in the list and marked as "ALIVE"
//iIfthe ping has higher incarnation value than the suspected record in the list, no more suspected (and disseminate)
                if(membershipList.unsuspectNode(event.getHeader().getSource(), pingInc)){
                    pbList.add(new InfoPiggyback(InfoType.ALIVENODE,event.getHeader().getSource(),pingInc), DISSEMINATION_VALUE);
                }
            }
            
//Decrease counter procedure before answering with a PONG message
            pbList.decreaseCounters();
            log.info("{} sending pong to partner: {}", new Object[]{selfAddress.getId(), event.getHeader().getSource().getId()});
            trigger(new NetPong(deepCopy(selfAddress), deepCopy(event.getHeader().getSource()), pbList.getList(), membershipList.getSelfIncarnation()), network);
        }
    };
    
    
    
    
    /*
    *
    *   P O N G received
    *
    */
    
    private Handler<NetPong> handlePong = new Handler<NetPong>() {
        
        @Override
        public void handle(NetPong event) {
            
            Integer pongInc = event.getContent().incarnationValue;
            printmyneigh();  //method activated only if debug flag is ON
            
//log.info("{} received pong from:{}", new Object[]{selfAddress.getId(), event.getHeader().getSource()});
            
//Cancel ack timeout if you receive a pong in time
            if(ackTimeoutIds.containsKey(event.getSource().getId())){
                cancelAck(event.getSource());
            }
            
//Control that the pong is from a suspected node, and unsuspect it in case of greater incarnation value in the PONG
            if(membershipList.unsuspectNode(event.getHeader().getSource(),pongInc))
                pbList.add(new InfoPiggyback(InfoType.ALIVENODE,event.getHeader().getSource(),membershipList.getIncarnationForMember(event.getHeader().getSource())), DISSEMINATION_VALUE);
            
            /*
            Indipendently from the execution, if received a PONG from a node in a indirect procedure, send back to the first
            requesor a "NetPingResp" message to inform that the node targeted is alive
            */
            Integer toremove = null;
            Integer toremovehelper = null;
            for(Integer indirectPinged : requestMapping.keySet()){
                if(indirectPinged==event.getHeader().getSource().getId()){
//log.info("Sending indirect ack to {} for node {}", new Object[]{requestorAddress,requestMapping.get(requestorAddress)});
                    NatedAddress requestorAddress = requestorsIndirect.get(requestMapping.get(indirectPinged));
                    trigger(new NetPingResp(deepCopy(selfAddress),deepCopy(requestorAddress),deepCopy(event.getHeader().getSource())),network);
                    if(requestorAddress!=null)
                        toremovehelper = requestorAddress.getId();
                    toremove = indirectPinged;
                }
            }
            if(toremove!=null) requestMapping.remove(toremove); //Note that multiple this mapping supports a single request at a time from the starting requestor! A subsequent request from the requestor overrides this.
            //if(toremovehelper!=null)requestorsIndirect.remove(toremovehelper);
            
            /*
            For each information packet in the PONG message, exevute certain operations (critical code)
            */
            if(event.getContent().infoList!=null && event.getContent().infoList.size()>0){
                for(Object ipb2 : event.getContent().infoList){
                    InfoPiggyback ipb = (InfoPiggyback)ipb2;
                    switch(ipb.getInfoType()){
                        
                        
                        case NEWNODE:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                if(membershipList.add(ipb.getInfoTarget(), ipb.getIncarnationValue()))
                                    pbList.add(new InfoPiggyback(InfoType.NEWNODE,ipb.getInfoTarget(),ipb.getIncarnationValue()), DISSEMINATION_VALUE);
                            } else {
//It is referred to me
                                if(ipb.getIncarnationValue()>membershipList.getSelfIncarnation()){
                                    membershipList.setSelfIncarnation(ipb.getIncarnationValue());
                                    pbList.add(new InfoPiggyback(InfoType.NEWNODE,ipb.getInfoTarget(),membershipList.getSelfIncarnation()), DISSEMINATION_VALUE);
                                }
                            }
                            break;
                            
                            
                        case DEADNODE:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                if(membershipList.remove(ipb.getInfoTarget(),ipb.getIncarnationValue())){
                                    pbList.add(new InfoPiggyback(InfoType.DEADNODE,ipb.getInfoTarget(),ipb.getIncarnationValue()), DISSEMINATION_VALUE);
                                    //trigger(new NetDeadNotification(selfAddress,selfAddress,ipb.getInfoTarget()),network);
                                }
                            } else {
//It is referred to me
                                if(membershipList.getSelfIncarnation()<=ipb.getIncarnationValue()){   
                                    membershipList.setSelfIncarnation(ipb.getIncarnationValue()+1);
                                    pbList.add(new InfoPiggyback(InfoType.NEWNODE,ipb.getInfoTarget(),membershipList.getSelfIncarnation()), DISSEMINATION_VALUE);
                                }
                            }
                            break;
                            
                            
                        case ALIVENODE:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                if(membershipList.unsuspectNode(ipb.getInfoTarget(),ipb.getIncarnationValue())){
                                    pbList.add(new InfoPiggyback(InfoType.ALIVENODE,ipb.getInfoTarget(),membershipList.getIncarnationForMember(ipb.getInfoTarget())), DISSEMINATION_VALUE);
                                    if(deadTimeoutIds.containsKey(ipb.getInfoTarget().getId())){
                                        cancelDeadTimeout(ipb.getInfoTarget());
                                    }
                                }
                            } else {
//It is referred to me
                                if(ipb.getIncarnationValue()>membershipList.getSelfIncarnation()){
                                    membershipList.setSelfIncarnation(ipb.getIncarnationValue());
                                    pbList.add(new InfoPiggyback(InfoType.ALIVENODE,ipb.getInfoTarget(),membershipList.getSelfIncarnation()), DISSEMINATION_VALUE);
                                }
                            }
                            break;
                            
                            
                        case SUSPECTEDNODE:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                
                                if(membershipList.suspectNode(ipb.getInfoTarget(),ipb.getIncarnationValue()))
                                    pbList.add(new InfoPiggyback(InfoType.SUSPECTEDNODE,ipb.getInfoTarget(),membershipList.getIncarnationForMember(ipb.getInfoTarget())), DISSEMINATION_VALUE);
//Check that there is no timeout associated to the event.getAddress already, otherwise the prev UUID is lost
//And start a new ack timer in case, so that to declare the node dead in case of timeout before ALIVE
                                if(!deadTimeoutIds.containsKey(ipb.getInfoTarget().getId()))
                                    scheduleDeadTimeout(ipb.getInfoTarget());
                            } else {
//It is referred to me
                                if(membershipList.getSelfIncarnation()<=ipb.getIncarnationValue()){ 
                                    membershipList.setSelfIncarnation(ipb.getIncarnationValue()+1);
                                    pbList.add(new InfoPiggyback(InfoType.ALIVENODE,deepCopy(selfAddress),membershipList.getSelfIncarnation()), DISSEMINATION_VALUE);
                                }
                            }
                            break;
                            
                        case NEWPARENT:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                if(membershipList.updateNewParents(ipb.getInfoTarget(),ipb.getInfoParent())){
                                    pbList.add(new InfoPiggyback(InfoType.NEWPARENT, ipb.getInfoTarget(),ipb.getInfoParent()),DISSEMINATION_VALUE);
                                }
                            }
                            break;
                        case DEADPARENT:
                            if(!ipb.getInfoTarget().getId().equals(selfAddress.getId())){
                                if(membershipList.updateDeadParents(ipb.getInfoTarget(),ipb.getInfoParent())){
                                    pbList.add(new InfoPiggyback(InfoType.DEADPARENT, ipb.getInfoTarget(),ipb.getInfoParent()),DISSEMINATION_VALUE);
                                }
                            }
                            break;
                    }
                }
            }
        }
    };
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    private final Handler<PingTimeout> handlePingTimeout = new Handler<PingTimeout>() {
        
        @Override
        public void handle(PingTimeout event) {
            
//Just configuration details
            if(membershipList.isEmpty()){
                cancelPeriodicPing();
                return;
            } else {
                if(pingTimeoutId==null){
                    schedulePeriodicPing();
                }
            }
            
            NatedAddress partnerAddress = membershipList.randomNode(); //Alive random node
//Check if still waiting for an answer from the node, if yes do not send to avoid interference with previous operation
            if(!ackTimeoutIds.containsKey(partnerAddress.getId())){
                log.info("{} sending ping to partner:{}", new Object[]{selfAddress.getId(), partnerAddress});
                scheduleAck(partnerAddress);
                trigger(new NetPing(deepCopy(selfAddress), deepCopy(partnerAddress), membershipList.getSelfIncarnation()), network);
            } else {
                log.info("{} FAIL sending ping to partner (waiting for ack):{}", new Object[]{selfAddress.getId(), partnerAddress});
            }
        }
    };
    
    
    
    
    
    /*
    *
    * Sending periodic details to the aggregator to print useful information
    *
    */
    private final Handler<StatusTimeout> handleStatusTimeout = new Handler<StatusTimeout>() {
        
        @Override
        public void handle(StatusTimeout event) {
// log.info("{} sending status to aggregator:{}", new Object[]{selfAddress.getId(), aggregatorAddress});
            int a=0;
            int n=0;
            int d=0;
            int s=0;
            int np=0;
            int dp=0;
            int ps=0;
            
            for(InfoPiggyback ipb : pbList.getList()){
                if(debugPB){
                    log.info("{} with selfInc {} PBContent: {} - {} - IVAL {} - DISS {} - CORRINCVAL {}", new Object[]{selfAddress.getId(), membershipList.getSelfIncarnation(), ipb.getInfoTarget().getId(), ipb.getInfoType(), ipb.getIncarnationValue(), pbList.getDissemination(ipb), membershipList.getIncarnationForMember(ipb.getInfoTarget())});
                }
//System.out.print("" + ipb.getInfoTarget().getId() + " " + ipb.getInfoType() + " - ");
                if(ipb.getInfoType()==InfoType.NEWNODE){
                    n++;
                } else if (ipb.getInfoType()==InfoType.DEADNODE){
                    d++;
                } else if (ipb.getInfoType()==InfoType.SUSPECTEDNODE){
                    s++;
                } else if (ipb.getInfoType()==InfoType.ALIVENODE){
                    a++;
                }  else if (ipb.getInfoType()==InfoType.NEWPARENT){
                    np++;
                } else if (ipb.getInfoType()==InfoType.DEADPARENT){
                    dp++;
                }
            }
            if(!selfAddress.isOpen()) ps = selfAddress.getParents().size();
            trigger(new NetStatus(deepCopy(selfAddress), aggregatorAddress, new Status(0, membershipList.size(), a, n, d, s, np, dp, ps, membershipList.getSelfIncarnation())), network);
        }
    };
    
    
    
    
    
    private final Handler<AckTimeout> handleAckTimeout = new Handler<AckTimeout>() {
        
        @Override
        public void handle(AckTimeout event) {
            
//Clean UUID for the event
            cancelAck(event.getAddress());
            
//If it is already suspected just avoid interference and end the handler
            if(!membershipList.isSuspected(event.getAddress())){
//Limiting the indirect procedure
                if(indTimeoutIds.size()>0){
//Do not operate the indirect procedure, but proceed with the normal if no interference
                    /*
                    if(!deleteTimeoutIds.containsKey(event.getAddress())){
                    membershipList.suspectNode(event.getAddress(),membershipList.getIncarnationForMember(event.getAddress()));
                    pbList.add(new InfoPiggyback(InfoType.SUSPECTEDNODE, event.getAddress(), membershipList.getIncarnationForMember(event.getAddress())),DISSEMINATION_VALUE);
                    trigger(new NetPing(selfAddress, event.getAddress(),membershipList.getSelfIncarnation()), network);
                    scheduleDeleteReq(event.getAddress());
                    }
                    */
                } else if (indTimeoutIds.isEmpty()) {
//Everything is free to proceed with the indirect procedure
                    membershipList.suspectNode(event.getAddress(),membershipList.getIncarnationForMember(event.getAddress()));
                        pbList.add(new InfoPiggyback(InfoType.SUSPECTEDNODE, event.getAddress(), membershipList.getIncarnationForMember(event.getAddress())),DISSEMINATION_VALUE);
                    
//Triggering the indirect procedure to random K nodes
                    ArrayList<NatedAddress> indirectPings = new ArrayList<NatedAddress>(K_VALUE);
                    try {
                        membershipList.selectRandom(indirectPings,K_VALUE);
                    } catch (Exception ex) {
                        java.util.logging.Logger.getLogger(SwimComp.class.getName()).log(Level.SEVERE, null, ex);
                    }
                    for(NatedAddress na : indirectPings){
                        if(!na.getId().equals(event.getAddress().getId())){
                            trigger(new NetPingReq(deepCopy(selfAddress),deepCopy(na),deepCopy(event.getAddress())),network);
                            //Check that there is no timeout associated to the event.getAddress already, otherwise the prev UUID is lost
//This is useless now, since we support a single indirect procedure per time
                            if(!indTimeoutIds.containsKey(event.getAddress().getId())){
//log.info("INDIR {} starts indirect procedure for node {}", new Object[]{selfAddress.getId(), event.getAddress()});
                                scheduleIndirectPing(event.getAddress());
                            }
                        }     
                    }
                }
            }
        }
    };
    
    
    
    
    
//Timer for the first stage of the indirect procedure
    private final Handler<IndTimeout> handleIndTimeout = new Handler<IndTimeout>() {
        
        @Override
        public void handle(IndTimeout event) {
//No answer from indirect pings
//log.info("INDIR {} didn't get any hack from random nodes for node {}", new Object[]{selfAddress.getId(), event.getAddress()});
            cancelIndTimeout(event.getAddress());
            if(membershipList.remove(event.getAddress(),membershipList.getIncarnationForMember(event.getAddress()))){
                pbList.add(new InfoPiggyback(InfoType.DEADNODE, event.getAddress(),membershipList.getIncarnationForMember(event.getAddress())),DISSEMINATION_VALUE);
                //trigger(new NetDeadNotification(selfAddress,selfAddress,event.getAddress()),network);
            }
        }
    };
    
    
    
    
//Handler for the frst stage of the indirect procedure
    private final Handler<NetPingResp> handlePingRespMsg = new Handler<NetPingResp>() {
        
        @Override
        public void handle(NetPingResp event) {
            cancelIndTimeout(event.getContent().toPing);
//log.info("INDIR {} received a single ack for node {}", new Object[]{selfAddress.getId(), event.getToPing()});
            if(membershipList.unsuspectNode(event.getContent().toPing,membershipList.getIncarnationForMember(event.getContent().toPing))){
                /*
                * Maybe disseminate here?
                */
                pbList.add(new InfoPiggyback(InfoType.ALIVENODE, event.getContent().toPing,membershipList.getIncarnationForMember(event.getContent().toPing)),DISSEMINATION_VALUE);
            }
        }
    };
    
    
    
//Indirect ping procedure: set a timer after which the possible answer in second stage is not forward to first stage
    private final Handler<DeleteTimeout> handleDeleteTimeout = new Handler<DeleteTimeout>() {
        
        @Override
        public void handle(DeleteTimeout event) {
            requestMapping.remove(event.getAddress().getId());
            cancelDeleteReq(event.getAddress());
        }
    };
    
    
    
//Indirect procedure: second stage initation
    private final Handler<NetPingReq> handlePingReqMsg = new Handler<NetPingReq>() {
        
        @Override
        public void handle(NetPingReq event) {
            requestMapping.put(event.getContent().toPing.getId(), event.getSource().getId());
            requestorsIndirect.put(event.getSource().getId(), deepCopy(event.getSource()));
            //Note that we do not operate on our list/piggiback information since we just act as second stage of the indirect procedure
            trigger(new NetPing(deepCopy(selfAddress), deepCopy(event.getContent().toPing),membershipList.getSelfIncarnation()), network);
           // if(membershipList.suspectNode(event.getContent().toPing,membershipList.getIncarnationForMember(event.getContent().toPing)))
              //pbList.add(new InfoPiggyback(InfoType.SUSPECTEDNODE, event.getContent().toPing, membershipList.getIncarnationForMember(event.getContent().toPing)),DISSEMINATION_VALUE);
            scheduleDeleteReq(event.getSource());
        }
    };
    
    
    
//Dead timeout handling (general, could be indirect second stage or direct procedure)
    private final Handler<DeadTimeout> handleDeadTimeout = new Handler<DeadTimeout>() {
        
        @Override
        public void handle(DeadTimeout event) {
//As usual, cleanup the UUID tables
            if(deadTimeoutIds.containsKey(event.getAddress().getId()))
                cancelDeadTimeout(event.getAddress());
            
            /*
            Note that the DeadTimeout can be triggered because of direct or indirect pinging. In the second case we just
            work for the first stage and do not operate on our list. We remove if the DeadTimeout is coming from our direct
            pinging that failed. If the pinging was our, the node has to be marked as SUSPECTED due to the previous
            missed reception of the Pong message
            */
            if(membershipList.isSuspected(event.getAddress())){
                membershipList.remove(event.getAddress(),membershipList.getIncarnationForMember(event.getAddress()));
                //trigger(new NetDeadNotification(selfAddress,selfAddress,event.getAddress()),network);
            }
        }
    };
    
    
    
    private final Handler<NetNewParent> handleNewParentMesg = new Handler<NetNewParent>() {
        
        @Override
        public void handle(NetNewParent event) {
            //Updating the parents list also in SwimComponent
            selfAddress=event.getSource();
            
            //Disseminating info about the new parent
            log.info("{} starting to disseminate NPInfo for {}", new Object[]{selfAddress.getId(), event.getContent().toPing});
            pbList.add(new InfoPiggyback(InfoType.NEWPARENT, selfAddress,event.getContent().toPing),DISSEMINATION_VALUE);
        }
    };
    
    
     private final Handler<NetDeadParent> handleDeadParentMesg = new Handler<NetDeadParent>() {
        
        @Override
        public void handle(NetDeadParent event) {
            //Updating the parents list also in SwimComponent
            selfAddress=event.getSource();
            
            //Disseminating info about the new parent
            pbList.add(new InfoPiggyback(InfoType.DEADPARENT, selfAddress,event.getContent().toPing),DISSEMINATION_VALUE);
        }
    };
    
    
    
    
    
//
//
//
//
//    Handler section is over
//
//
//
//
    
    
    
    
    private void schedulePeriodicPing() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(PERIODIC_PING, PERIODIC_PING);
        PingTimeout sc = new PingTimeout(spt);
        spt.setTimeoutEvent(sc);
        pingTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    private void cancelPeriodicPing() {
        CancelTimeout cpt = new CancelTimeout(pingTimeoutId);
        trigger(cpt, timer);
        pingTimeoutId = null;
    }
    
    private void schedulePeriodicStatus() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(PERIODIC_STATUS, PERIODIC_STATUS);
        StatusTimeout sc = new StatusTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    
    private void cancelPeriodicStatus() {
        CancelTimeout cpt = new CancelTimeout(statusTimeoutId);
        trigger(cpt, timer);
        statusTimeoutId = null;
    }
    
    private void scheduleAck(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(ACK_TIMER);
        AckTimeout sc = new AckTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        ackTimeoutIds.put(address.getId(),sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelAck(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(ackTimeoutIds.get(address.getId()));
        trigger(cpt, timer);
        ackTimeoutIds.remove(address.getId());
    }
    
    private void scheduleDeleteReq(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(DELETE_REQ_TIMER);
        DeleteTimeout sc = new DeleteTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        deleteTimeoutIds.put(address.getId(),sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelDeleteReq(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(deleteTimeoutIds.get(address.getId()));
        trigger(cpt, timer);
        deleteTimeoutIds.remove(address.getId());
    }
    
    private void scheduleDeadTimeout(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(DEAD_TIMER);
        DeadTimeout sc = new DeadTimeout(spt, address);
        spt.setTimeoutEvent(sc);
        deadTimeoutIds.put(address.getId(),sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void scheduleIndirectPing(NatedAddress address) {
        ScheduleTimeout spt = new ScheduleTimeout(INDIRECT_PING_TIMER);
        IndTimeout sc = new IndTimeout(spt,address);
        spt.setTimeoutEvent(sc);
        indTimeoutIds.put(address.getId(), sc.getTimeoutId());
        trigger(spt, timer);
    }
    
    private void cancelIndTimeout(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(indTimeoutIds.get(address.getId()));
        trigger(cpt, timer);
        indTimeoutIds.remove(address.getId());
    }
    
    private void cancelDeadTimeout(NatedAddress address) {
        CancelTimeout cpt = new CancelTimeout(deadTimeoutIds.get(address.getId()));
        trigger(cpt, timer);
        deadTimeoutIds.remove(address.getId());
    }
    
    public static class SwimInit extends Init<SwimComp> {
        
        public final NatedAddress selfAddress;
        public final Set<NatedAddress> bootstrapNodes;
        public final NatedAddress aggregatorAddress;
        
        public SwimInit(NatedAddress selfAddress, Set<NatedAddress> bootstrapNodes, NatedAddress aggregatorAddress) {
            this.selfAddress = selfAddress;
            this.bootstrapNodes = bootstrapNodes;
            this.aggregatorAddress = aggregatorAddress;
        }
    }
    
    private static class StatusTimeout extends Timeout {
        
        public StatusTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class PingTimeout extends Timeout {
        
        public PingTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    private static class IndTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public IndTimeout(ScheduleTimeout request, NatedAddress address) {
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
    
    private static class DeleteTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public DeleteTimeout(ScheduleTimeout request, NatedAddress address) {
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
    
    private static class AckTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public AckTimeout(ScheduleTimeout request, NatedAddress address) {
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
    
    private static class DeadTimeout extends Timeout {
        
        private final NatedAddress address;
        
        public DeadTimeout(ScheduleTimeout request, NatedAddress address) {
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
    
//Supporting function for debug purposes
    private void printmyneigh() {
        membershipList.printNeighbour();
    }
    
    private NatedAddress deepCopy(NatedAddress na){
        if(na == null) return null;
        NatedAddress na2 = new BasicNatedAddress(new BasicAddress(na.getIp(), na.getPort(), na.getId()), na.getNatType(), na.getParents());
        return na2;
    }
}

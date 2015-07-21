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

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.kth.swim.msg.net.NetStatus;
import se.sics.kompics.ComponentDefinition;
import se.sics.kompics.Handler;
import se.sics.kompics.Init;
import se.sics.kompics.Positive;
import se.sics.kompics.Start;
import se.sics.kompics.Stop;
import se.sics.kompics.network.Network;
import se.sics.kompics.timer.SchedulePeriodicTimeout;
import se.sics.kompics.timer.Timeout;
import se.sics.kompics.timer.Timer;
import se.sics.p2ptoolbox.util.network.NatedAddress;

/**
 * @author Alex Ormenisan <aaor@sics.se>
 */
public class AggregatorComp extends ComponentDefinition {
    
    private static final Logger log = LoggerFactory.getLogger(AggregatorComp.class);
    private Positive<Network> network = requires(Network.class);
    private Positive<Timer> timer = requires(Timer.class);
    
    private UUID statusTimeoutId;
    
    private final NatedAddress selfAddress;
    
    public AggregatorComp(AggregatorInit init) {
        this.selfAddress = init.selfAddress;
        log.info("{} initiating...", new Object[]{selfAddress.getId()});
        
        subscribe(handleStart, control);
        subscribe(handleStop, control);
        subscribe(handleStatus, network);
        subscribe(handlePrintTimeout,timer);
    }
    
    private Handler<Start> handleStart = new Handler<Start>() {
        
        @Override
        public void handle(Start event) {
            log.info("{} starting...", new Object[]{selfAddress});
            schedulePeriodicPrint();
        }
        
    };
    private Handler<Stop> handleStop = new Handler<Stop>() {
        
        @Override
        public void handle(Stop event) {
            log.info("{} stopping...", new Object[]{selfAddress});
        }
        
    };
    
    private Handler<NetStatus> handleStatus = new Handler<NetStatus>() {
        
        @Override
        public void handle(NetStatus status) {
            log.info("AGGREGATOR status from {} >>> {}",
                    new Object[]{ status.getHeader().getSource().getId(), status.getContent().memberSize});
            
            log.info("{} PBAlive {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().alivePB});
            log.info("{} PBDead {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().deadPB});
            log.info("{} PBSuspected {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().suspectedPB});
            log.info("{} PBNew {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().newPB});
            log.info("{} PBParentNew {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().newParentPB});
            log.info("{} PBParentDead  {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().deadParentPB});     
            log.info("{} SelfIncarnation  {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().incarnationValue});   
            //log.info("{} ParentListSize  {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().parentsSize}); 
            if(status.getContent().parentsSize!=0){
                log.info("{} ParentListSize  {}", new Object[]{status.getHeader().getSource().getId(), status.getContent().parentsSize}); 
            }
            
        }
    };
    
    public static class AggregatorInit extends Init<AggregatorComp> {
        
        public final NatedAddress selfAddress;
        
        public AggregatorInit(NatedAddress selfAddress) {
            this.selfAddress = selfAddress;
        }
    }
    
    
    private Handler<PrintTimeout> handlePrintTimeout = new Handler<PrintTimeout>() {
        
        @Override
        public void handle(PrintTimeout event) {
        }
    };
    
    private void schedulePeriodicPrint() {
        SchedulePeriodicTimeout spt = new SchedulePeriodicTimeout(1000, 1000);
        PrintTimeout sc = new PrintTimeout(spt);
        spt.setTimeoutEvent(sc);
        statusTimeoutId = sc.getTimeoutId();
        trigger(spt, timer);
    }
    
    
    private static class PrintTimeout extends Timeout {
        
        public  PrintTimeout(SchedulePeriodicTimeout request) {
            super(request);
        }
    }
    
    
}

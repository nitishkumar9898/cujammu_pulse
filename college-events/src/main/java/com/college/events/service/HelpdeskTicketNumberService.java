package com.college.events.service;

import com.college.events.model.SupportTicketCounter;
import com.college.events.repository.SupportTicketCounterRepository;
import com.college.events.repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Allocations survive app restarts. Failed outer commands may leave gaps, never reused numbers. */
@Service
public class HelpdeskTicketNumberService {
    private final SupportTicketCounterRepository counters;
    private final SupportTicketRepository tickets;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Autowired
    public HelpdeskTicketNumberService(SupportTicketCounterRepository counters, SupportTicketRepository tickets,
                                      PlatformTransactionManager manager) {
        this(counters,tickets,manager,Clock.systemUTC());
    }
    HelpdeskTicketNumberService(SupportTicketCounterRepository counters, SupportTicketRepository tickets,
                               PlatformTransactionManager manager,Clock clock) {
        this.counters=counters;this.tickets=tickets;this.clock=clock;
        this.transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(10);
    }

    public String nextNumber() {
        if(TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Allocate ticket numbers before opening a domain transaction");
        String month=YearMonth.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).format(DateTimeFormatter.ofPattern("yyyyMM"));
        String prefix="CUJ-TKT-"+month+"-";
        for(int attempt=0;attempt<5;attempt++) {
            try {
                Long number=transaction.execute(status -> {
                    SupportTicketCounter counter=counters.lockMonth(month).orElseGet(() ->
                        counters.saveAndFlush(new SupportTicketCounter(month,highestExisting(prefix))));
                    if(counter.getLastNumber()<0 || counter.getLastNumber()==Long.MAX_VALUE)
                        throw new IllegalStateException("Ticket counter requires operator recovery");
                    counter.setLastNumber(counter.getLastNumber()+1);
                    counters.saveAndFlush(counter);
                    return counter.getLastNumber();
                });
                if(number==null)throw new IllegalStateException("Ticket allocation did not complete");
                return prefix+String.format(Locale.ROOT,"%04d",number);
            } catch(DataIntegrityViolationException | ConcurrencyFailureException failure) {
                // Concurrent first creation rolls back fully before bounded retry.
                if(attempt==4)throw failure;
            }
        }
        throw new IllegalStateException("Ticket number allocation failed");
    }

    private long highestExisting(String prefix) {
        long highest=0;
        for(String number:tickets.findNumberHistory(prefix+"%")) {
            String suffix=number.substring(prefix.length());
            if(!suffix.matches("[0-9]{4,19}"))throw new IllegalStateException("Existing monthly ticket number requires review");
            highest=Math.max(highest,Long.parseLong(suffix));
        }
        return highest;
    }
}

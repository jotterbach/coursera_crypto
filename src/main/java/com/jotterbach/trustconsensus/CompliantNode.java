package com.jotterbach.trustconsensus;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/* CompliantNode refers to a node that follows the rules (not malicious)*/
public class CompliantNode implements Node {

    private double p_graph;
    private double p_malicious;
    private double p_txDistribution;
    private int numRounds;

    private boolean[] followees;

    private Set<Transaction> pendingTransactions = new HashSet<>();

    private Set<Integer> blacklist = new HashSet<>();

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.p_graph = p_graph;
        this.p_malicious = p_malicious;
        this.p_txDistribution = p_txDistribution;
        this.numRounds = numRounds;
    }

    public void setFollowees(boolean[] followees) {
        this.followees = followees;
    }

    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        Set<Transaction> unseenTxn = pendingTransactions
                .stream()
                .filter(tx -> !this.pendingTransactions.contains(tx))
                .collect(Collectors.toSet());
        this.pendingTransactions.addAll(unseenTxn);
    }

    public Set<Transaction> sendToFollowers() {
        Set<Transaction> txnToSend = new HashSet<>(this.pendingTransactions);
        this.pendingTransactions.clear();
        return txnToSend;
    }

    public void receiveFromFollowees(Set<Candidate> candidates) {
        updateBlacklist(candidates);
        Set<Candidate> validCandidates = candidates
                .stream()
                .filter(candidate -> this.followees[candidate.sender] && !this.blacklist.contains(candidate.sender))
                .collect(Collectors.toSet());

        validCandidates.forEach(candidate -> this.pendingTransactions.add(candidate.tx));
    }

    private void updateBlacklist(Set<Candidate> candidates) {
        // Need to blacklist nodes that signed up as a follower, but do not send anything.
        // Strategy:
        // 1. Get the IDs of all senders
        // 2. Loop through the followees and identify those who are in the group of followees but did not submit a
        //    candidate transaction.
        // 3. Add those to a blacklist

        Set<Integer> senderIds = candidates
                .stream()
                .map(candidate -> candidate.sender)
                .collect(Collectors.toSet());
        for (int i = 0; i < this.followees.length; i++) {
            if (this.followees[i] && !senderIds.contains(i)) {
                this.blacklist.add(i);
            }
        }
    }

}

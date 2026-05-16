package com.sync.controller;

import com.sync.common.annotation.LoginUser;
import com.sync.dto.vote.GroupVoteStatusResponse;
import com.sync.dto.vote.VotePlaceResponse;
import com.sync.dto.vote.VoteRequest;
import com.sync.dto.vote.VoteResponse;
import com.sync.dto.vote.VoteStatusResponse;
import com.sync.service.VoteService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bands/{bandId}/votes")
public class VoteController {

    private final VoteService voteService;

    public VoteController(VoteService voteService) {
        this.voteService = voteService;
    }

    @GetMapping("/places")
    public ResponseEntity<List<VotePlaceResponse>> getVotablePlaces(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(voteService.getVotablePlaces(userId, bandId));
    }

    @PostMapping
    public ResponseEntity<VoteResponse> castVote(
            @LoginUser Long userId,
            @PathVariable Long bandId,
            @RequestBody VoteRequest request
    ) {
        return ResponseEntity.ok(voteService.castVote(userId, bandId, request));
    }

    @GetMapping("/status")
    public ResponseEntity<VoteStatusResponse> getVoteStatus(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(voteService.getVoteStatus(userId, bandId));
    }

    @GetMapping("/status/group")
    public ResponseEntity<GroupVoteStatusResponse> getGroupVoteStatus(
            @LoginUser Long userId,
            @PathVariable Long bandId
    ) {
        return ResponseEntity.ok(voteService.getGroupVoteStatus(userId, bandId));
    }
}

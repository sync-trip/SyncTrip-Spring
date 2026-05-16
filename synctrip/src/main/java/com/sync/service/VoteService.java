package com.sync.service;

import com.sync.domain.band.Band;
import com.sync.domain.band.BandMember;
import com.sync.domain.band.BandStatus;
import com.sync.domain.place.Place;
import com.sync.domain.user.User;
import com.sync.domain.vote.Vote;
import com.sync.dto.vote.GroupVoteStatusResponse;
import com.sync.dto.vote.MemberVoteStatus;
import com.sync.dto.ws.VoteEvent;
import com.sync.dto.vote.VotePlaceResponse;
import com.sync.dto.vote.VoteRequest;
import com.sync.dto.vote.VoteResponse;
import com.sync.dto.vote.VoteStatusResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class VoteService {

    private final BandRepository bandRepository;
    private final BandMemberRepository bandMemberRepository;
    private final PlaceRepository placeRepository;
    private final PlaceBookmarkRepository placeBookmarkRepository;
    private final VoteRepository voteRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public VoteService(BandRepository bandRepository,
                       BandMemberRepository bandMemberRepository,
                       PlaceRepository placeRepository,
                       PlaceBookmarkRepository placeBookmarkRepository,
                       VoteRepository voteRepository,
                       UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<VotePlaceResponse> getVotablePlaces(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireVotingStatus(band);

        List<Place> places = placeRepository.findAllByBandId(bandId);

        Set<Long> myBookmarkPlaceIds = placeBookmarkRepository
                .findByBandIdAndUserIdOrderByCreatedAtDesc(bandId, userId)
                .stream()
                .map(pb -> pb.getPlace().getId())
                .collect(Collectors.toSet());

        return places.stream()
                .map(p -> new VotePlaceResponse(
                        p.getId(),
                        p.getApiSource(),
                        p.getName(),
                        p.getCategory(),
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getAddress(),
                        p.getRating(),
                        p.getThumbnailUrl(),
                        myBookmarkPlaceIds.contains(p.getId())
                ))
                .toList();
    }

    public VoteResponse castVote(Long userId, Long bandId, VoteRequest request) {
        User user = loadActiveUser(userId);
        Band band = loadBand(bandId);
        BandMember member = loadBandMember(bandId, userId);

        requireVotingStatus(band);

        if (member.isJoinedAfterVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "투표 이후 합류한 멤버는 투표할 수 없습니다.");
        }

        if (request.result() != 1 && request.result() != -1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "result는 1(LIKE) 또는 -1(DISLIKE)만 허용됩니다.");
        }

        Place place = placeRepository.findById(request.placeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));

        if (voteRepository.existsByBandIdAndUserIdAndPlaceId(bandId, userId, place.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 투표한 장소입니다.");
        }

        // 본인이 담은 장소는 result=0(자동 LIKE)으로 고정
        boolean isMyBookmark = placeBookmarkRepository
                .existsByBandIdAndUserIdAndPlaceId(bandId, userId, place.getId());
        int result = isMyBookmark ? 0 : request.result();

        Vote vote = Vote.create(band, user, place, result);
        voteRepository.save(vote);

        int totalPlaces = placeRepository.findAllByBandId(bandId).size();
        int myVotedCount = (int) voteRepository.countByBandIdAndUserId(bandId, userId);
        messagingTemplate.convertAndSend("/topic/bands/" + bandId + "/votes",
                new VoteEvent(userId, place.getId(), myVotedCount, totalPlaces));

        return new VoteResponse(vote.getId(), place.getId(), vote.getResult(), vote.getVotedAt());
    }

    @Transactional(readOnly = true)
    public VoteStatusResponse getVoteStatus(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireVotingStatus(band);

        int totalPlaces = placeRepository.findAllByBandId(bandId).size();
        int myVotedCount = (int) voteRepository.countByBandIdAndUserId(bandId, userId);

        return new VoteStatusResponse(totalPlaces, myVotedCount, myVotedCount >= totalPlaces);
    }

    @Transactional(readOnly = true)
    public GroupVoteStatusResponse getGroupVoteStatus(Long userId, Long bandId) {
        loadActiveUser(userId);
        Band band = loadBand(bandId);
        requireVotingStatus(band);

        int totalPlaces = placeRepository.findAllByBandId(bandId).size();

        List<BandMember> votingMembers = bandMemberRepository.findByBandId(bandId)
                .stream()
                .filter(m -> !m.isJoinedAfterVoting())
                .toList();

        List<MemberVoteStatus> memberStatuses = votingMembers.stream()
                .map(m -> {
                    int voted = (int) voteRepository.countByBandIdAndUserId(bandId, m.getUser().getId());
                    return new MemberVoteStatus(
                            m.getUser().getId(),
                            m.getUser().getName(),
                            voted,
                            voted >= totalPlaces
                    );
                })
                .toList();

        return new GroupVoteStatusResponse(totalPlaces, votingMembers.size(), memberStatuses);
    }

    private void requireVotingStatus(Band band) {
        if (band.getStatus() != BandStatus.VOTING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "투표는 VOTING 단계에서만 가능합니다.");
        }
    }

    private User loadActiveUser(Long userId) {
        return userRepository.findByIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private Band loadBand(Long bandId) {
        return bandRepository.findById(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private BandMember loadBandMember(Long bandId, Long userId) {
        return bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 투표할 수 있습니다."));
    }
}

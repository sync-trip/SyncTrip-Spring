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
import com.sync.dto.vote.VotePlaceResultResponse;
import com.sync.dto.vote.VoteRequest;
import com.sync.dto.vote.VoteResponse;
import com.sync.dto.vote.VoteStatusResponse;
import com.sync.repository.BandMemberRepository;
import com.sync.repository.BandRepository;
import com.sync.repository.PlaceBookmarkRepository;
import com.sync.repository.PlaceRepository;
import com.sync.repository.UserRepository;
import com.sync.repository.VoteRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final BandService bandService;

    public VoteService(BandRepository bandRepository,
                       BandMemberRepository bandMemberRepository,
                       PlaceRepository placeRepository,
                       PlaceBookmarkRepository placeBookmarkRepository,
                       VoteRepository voteRepository,
                       UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate,
                       BandService bandService) {
        this.bandRepository = bandRepository;
        this.bandMemberRepository = bandMemberRepository;
        this.placeRepository = placeRepository;
        this.placeBookmarkRepository = placeBookmarkRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.bandService = bandService;
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

        Map<Long, Integer> myVoteResultMap = voteRepository.findByBandIdAndUserId(bandId, userId)
                .stream()
                .collect(Collectors.toMap(v -> v.getPlace().getId(), Vote::getResult));

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
                        myBookmarkPlaceIds.contains(p.getId()),
                        myVoteResultMap.getOrDefault(p.getId(), null)
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

        // 본인이 모든 장소에 투표 완료했으면 개인 플래그 설정
        if (myVotedCount >= totalPlaces) {
            member.markVoteCompleted();
            bandMemberRepository.save(member);
        }
        // 투표 자격 있는 전원의 완료 플래그가 모두 세팅된 경우에만 자동 마감
        List<BandMember> votingMembers = bandMemberRepository.findByBandId(bandId).stream()
                .filter(m -> !m.isJoinedAfterVoting())
                .toList();
        boolean allCompleted = !votingMembers.isEmpty() &&
                votingMembers.stream().allMatch(BandMember::isVoteCompleted);
        if (allCompleted) {
            bandService.finishVoting(bandId);
        }

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
                    // 완료 여부는 DB에 저장된 플래그 사용 (동적 계산 대비 PlaceBookmark 변동 영향 없음)
                    return new MemberVoteStatus(
                            m.getUser().getId(),
                            m.getUser().getName(),
                            voted,
                            m.isVoteCompleted()
                    );
                })
                .toList();

        return new GroupVoteStatusResponse(totalPlaces, votingMembers.size(), memberStatuses);
    }

    /**
     * 투표 결과 조회 — 장소별 좋아요/싫어요 집계.
     * VOTING 종료 후 GENERATING 상태에서도 호출 가능하도록 상태 검사 없음.
     */
    @Transactional(readOnly = true)
    public List<VotePlaceResultResponse> getVoteResults(Long userId, Long bandId) {
        loadActiveUser(userId);
        loadBand(bandId);

        List<Place> places = placeRepository.findAllByBandId(bandId);
        List<Vote> allVotes = voteRepository.findByBandId(bandId);

        long eligibleVoters = bandMemberRepository.countEligibleVoters(bandId);
        // 과반수 기준: eligibleVoters가 0이면 최소 1표 이상을 요구
        int threshold = eligibleVoters > 0 ? (int) Math.ceil(eligibleVoters * 0.5) : 1;

        Map<Long, Long> likeMap = allVotes.stream()
                .filter(v -> v.getResult() >= 0)
                .collect(Collectors.groupingBy(v -> v.getPlace().getId(), Collectors.counting()));
        Map<Long, Long> dislikeMap = allVotes.stream()
                .filter(v -> v.getResult() < 0)
                .collect(Collectors.groupingBy(v -> v.getPlace().getId(), Collectors.counting()));

        Map<Long, Integer> myVoteMap = voteRepository.findByBandIdAndUserId(bandId, userId)
                .stream()
                .collect(Collectors.toMap(v -> v.getPlace().getId(), Vote::getResult));

        return places.stream()
                .map(p -> {
                    int likeCount    = likeMap.getOrDefault(p.getId(), 0L).intValue();
                    int dislikeCount = dislikeMap.getOrDefault(p.getId(), 0L).intValue();
                    return new VotePlaceResultResponse(
                            p.getId(),
                            p.getName(),
                            p.getCategory(),
                            p.getThumbnailUrl(),
                            p.getAddress(),
                            p.getLatitude(),
                            p.getLongitude(),
                            likeCount,
                            dislikeCount,
                            likeCount >= threshold,
                            myVoteMap.get(p.getId())
                    );
                })
                // 좋아요 많은 순 정렬 (통과 장소 상단 노출)
                .sorted(Comparator.comparingInt(VotePlaceResultResponse::likeCount).reversed())
                .toList();
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
        return bandRepository.findByIdAndIsDeletedFalse(bandId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "밴드를 찾을 수 없습니다."));
    }

    private BandMember loadBandMember(Long bandId, Long userId) {
        return bandMemberRepository.findByBandIdAndUserId(bandId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "밴드 멤버만 투표할 수 있습니다."));
    }
}

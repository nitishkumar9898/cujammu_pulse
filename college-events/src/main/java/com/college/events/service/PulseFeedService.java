package com.college.events.service;

import com.college.events.model.PostComment;
import com.college.events.model.PostLike;
import com.college.events.model.PulsePost;
import com.college.events.model.Role;
import com.college.events.model.User;
import com.college.events.repository.PostCommentRepository;
import com.college.events.repository.PostLikeRepository;
import com.college.events.repository.PulsePostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PulseFeedService {

    private final PulsePostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostLikeRepository likeRepository;

    @Transactional
    public PulsePost createPost(User author, String content, String mediaUrl, String mediaType,
                               String category, Long eventId, String eventTitle,
                               String winnerName, String winningPrize, boolean isWinner) {
        PulsePost post = PulsePost.builder()
                .author(author)
                .content(content)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType != null ? mediaType : "NONE")
                .category(category != null ? category : "GENERAL")
                .relatedEventId(eventId)
                .relatedEventTitle(eventTitle)
                .winnerName(winnerName)
                .winningPrize(winningPrize)
                .isWinnerAnnouncement(isWinner)
                .likesCount(0)
                .commentsCount(0)
                .sharesCount(0)
                .pinned(isWinner) // Winner showcases pinned by default
                .build();

        return postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Page<PulsePost> getFeed(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 50));
        return postRepository.findAllByOrderByPinnedDescCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<PulsePost> getPostsByCategory(String category) {
        return postRepository.findByCategoryOrderByCreatedAtDesc(category);
    }

    @Transactional(readOnly = true)
    public List<PulsePost> getVictoryShowcases() {
        return postRepository.findByIsWinnerAnnouncementTrueOrderByCreatedAtDesc();
    }

    @Transactional
    public Map<String, Object> toggleLike(Long postId, User user) {
        Map<String, Object> result = new HashMap<>();
        Optional<PostLike> existing = likeRepository.findByPostIdAndUserId(postId, user.getId());
        PulsePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
            post.setLikesCount(Math.max(0, post.getLikesCount() - 1));
            result.put("liked", false);
        } else {
            likeRepository.save(PostLike.builder().postId(postId).userId(user.getId()).build());
            post.setLikesCount(post.getLikesCount() + 1);
            result.put("liked", true);
        }
        postRepository.save(post);
        result.put("likesCount", post.getLikesCount());
        return result;
    }

    @Transactional(readOnly = true)
    public boolean isLikedByUser(Long postId, Long userId) {
        return likeRepository.existsByPostIdAndUserId(postId, userId);
    }

    @Transactional
    public PostComment addComment(Long postId, User author, String content) {
        PulsePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        PostComment comment = PostComment.builder()
                .post(post)
                .author(author)
                .content(content.trim())
                .build();

        PostComment saved = commentRepository.save(comment);
        post.setCommentsCount(post.getCommentsCount() + 1);
        postRepository.save(post);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PostComment> getComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }

    @Transactional
    public void deletePost(Long postId, User requester) {
        PulsePost post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        if (!post.getAuthor().getId().equals(requester.getId()) && requester.getRole() != Role.ADMIN) {
            throw new SecurityException("Unauthorized to delete this post");
        }
        likeRepository.deleteByPostId(postId);
        commentRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Transactional
    public void incrementShare(Long postId) {
        postRepository.findById(postId).ifPresent(post -> {
            post.setSharesCount(post.getSharesCount() + 1);
            postRepository.save(post);
        });
    }
}

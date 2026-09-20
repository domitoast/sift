package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FetchJobTest {
    private static final Long SOURCE_ID = 1L;

    @Test
    @DisplayName("新建立的任務是 PENDING，而且還沒有 startedAt")
    void newJob_shouldBePending() {
        FetchJob job = new FetchJob(SOURCE_ID);

        assertThat(job.getStatus()).isEqualTo(FetchStatus.PENDING);
        assertThat(job.getStartedAt()).isNull();
        assertThat(job.isFinished()).isFalse();
    }

    @Test
    @DisplayName("PENDING → RUNNING，同時記下 startedAt")
    void start_fromPending_shouldRunAndRecordTime() {
        FetchJob job = new FetchJob(SOURCE_ID);

        job.start();

        assertThat(job.getStatus()).isEqualTo(FetchStatus.RUNNING);

        assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("RUNNING → SUCCESS，同時記下 finishedAt 與抓到幾篇")
    void succeed_fromRunning_shouldFinish() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();

        job.succeed(7);

        assertThat(job.getStatus()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.isFinished()).isTrue();

        assertThat(job.getNewItemCount()).isEqualTo(7);
    }

    @Test
    @DisplayName("RUNNING → FAILED，同時記下原因")
    void fail_fromRunning_shouldRecordReason() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();

        job.fail(FailureType.TRANSIENT, "connect timed out");

        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.getFailureType()).isEqualTo(FailureType.TRANSIENT);
        assertThat(job.getFailureReason()).isEqualTo("connect timed out");
    }

    @Test
    @DisplayName("★ PENDING 不能直接 succeed——還沒開始怎麼會成功")
    void succeed_fromPending_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);

        assertThatThrownBy(() -> job.succeed(5))
                .isInstanceOf(IllegalFetchJobTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SUCCESS");
    }

    @Test
    @DisplayName("★ SUCCESS 是終點，不能再 start")
    void start_fromSuccess_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();
        job.succeed(0);

        assertThatThrownBy(job::start)
                .isInstanceOf(IllegalFetchJobTransitionException.class);
    }

    @Test
    @DisplayName("★ FAILED 是終點，不能再 start（ADR-008：失敗不重跑，開新的一筆）")
    void start_fromFailed_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();
        job.fail(FailureType.PERMANENT, "404 Not Found");

        assertThatThrownBy(job::start)
                .isInstanceOf(IllegalFetchJobTransitionException.class);
    }

    @Test
    @DisplayName("★ 已經 RUNNING 的不能再 start——防的是同一筆被兩個執行緒同時撿走")
    void start_fromRunning_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();

        assertThatThrownBy(job::start)
                .isInstanceOf(IllegalFetchJobTransitionException.class);
    }

    @Test
    @DisplayName("★ 失敗之後不能改口說成功")
    void succeed_fromFailed_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();
        job.fail(FailureType.TRANSIENT, "read timed out");

        assertThatThrownBy(() -> job.succeed(3))
                .isInstanceOf(IllegalFetchJobTransitionException.class);

        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("read timed out");
        assertThat(job.getNewItemCount()).isZero();
    }

    @Test
    @DisplayName("★ PENDING 可以直接失敗，而且 startedAt 保持 null")
    void fail_fromPending_shouldFailWithoutStartedAt() {
        FetchJob job = new FetchJob(SOURCE_ID);

        job.fail(FailureType.TRANSIENT, "系統忙碌中，請稍後再試");

        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);

        assertThat(job.getStartedAt()).isNull();
        assertThat(job.getFinishedAt()).isNotNull();
    }

    @Test
    @DisplayName("★ 已經結束的任務不能再失敗一次——否則回收排程會覆蓋掉真正的失敗原因")
    void fail_fromFinished_shouldThrow() {
        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();
        job.fail(FailureType.PERMANENT, "404 Not Found");

        assertThatThrownBy(() -> job.fail(FailureType.TRANSIENT, "執行中斷"))
                .isInstanceOf(IllegalFetchJobTransitionException.class);

        assertThat(job.getFailureReason()).isEqualTo("404 Not Found");
        assertThat(job.getFailureType()).isEqualTo(FailureType.PERMANENT);
    }
}

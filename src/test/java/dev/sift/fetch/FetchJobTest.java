package dev.sift.fetch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FetchJob 狀態機的 unit test。
 *
 * <p><b>注意這個檔案沒有 {@code @SpringBootTest}。</b>
 * 沒有 Spring、沒有資料庫、沒有 mock——就是 new 一個物件呼叫方法。
 *
 * <p>「SUCCESS 可不可以變回 RUNNING」這個問題跟 PostgreSQL 一點關係都沒有，
 * 所以這裡不需要資料庫。整個檔案跑不到 100 毫秒。
 */
class FetchJobTest {

    private static final Long SOURCE_ID = 1L;

    // ---------- 合法的路徑 ----------

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

        /*
         * 這一行是重點。
         *
         * 資料庫的 ck_fetch_job_started 約束要求
         * 「離開 PENDING 之後 started_at 不可以是 null」。
         *
         * 如果 start() 只改了 status 忘了記時間，
         * 這裡會紅——而不是等到寫入資料庫時才爆。
         */
        assertThat(job.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("RUNNING → SUCCESS，同時記下 finishedAt")
    void succeed_fromRunning_shouldFinish() {

        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();

        job.succeed();

        assertThat(job.getStatus()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(job.getFinishedAt()).isNotNull();
        assertThat(job.isFinished()).isTrue();
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

    // ---------- 不合法的路徑 ----------

    @Test
    @DisplayName("★ PENDING 不能直接 succeed——還沒開始怎麼會成功")
    void succeed_fromPending_shouldThrow() {

        FetchJob job = new FetchJob(SOURCE_ID);

        assertThatThrownBy(job::succeed)
                .isInstanceOf(IllegalFetchJobTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SUCCESS");
    }

    @Test
    @DisplayName("★ SUCCESS 是終點，不能再 start")
    void start_fromSuccess_shouldThrow() {

        FetchJob job = new FetchJob(SOURCE_ID);
        job.start();
        job.succeed();

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

        assertThatThrownBy(job::succeed)
                .isInstanceOf(IllegalFetchJobTransitionException.class);

        // 光是丟例外不夠——要確認原本的失敗紀錄沒有被污染
        assertThat(job.getStatus()).isEqualTo(FetchStatus.FAILED);
        assertThat(job.getFailureReason()).isEqualTo("read timed out");
    }
}

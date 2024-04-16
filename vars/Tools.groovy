import org.manci.Logger
import org.manci.WarningException

class Tools {
    def PR_TITLE_CHECK_REX = /(\[)(feat|fix|build|docs|style|refactor|perf|test|revert|chore|upgrade|devops)((\(.+\))?)\](:)( )(.{1,50})([\s\S]*)$/
    def PR_COMMIT_CHECK_REX = /(\[)(feat|fix|build|docs|style|refactor|perf|test|revert|chore|upgrade|devops)((\(.+\))?)\](:)( )(.{1,50})([\s\S]*)$/
    Logger logger
    def script
    Tools(script) {
        this.script = script
        logger = new Logger(script)
    }

    def setPrTiTleCheckRex(String rex) {
        PR_TITLE_CHECK_REX = rex
    }
    def setPrCommitCheckRex(String rex) {
        PR_COMMIT_CHECK_REX = rex
    }

    def checkPrTitle(String prTitle) {
        def matcher = (prTitle ==~ PR_TITLE_CHECK_REX)
        logger.debug("checkPrTitle: ${matcher}")
        if (matcher != true){
            throw new WarningException("PR提交不规范, 内容: ${prTitle}")
        }
    }

    def checkPrCommits(List<String> prCommits ) {
        prCommits.each {
            logger.debug("checkPrCommits: ${it}")
            def matcher = (it ==~ PR_COMMIT_CHECK_REX)
            if (matcher != true){
                throw new WarningException("Commit提交不规范, 内容: ${it}")
            }
        }
    }
}

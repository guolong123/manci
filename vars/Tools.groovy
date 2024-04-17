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
        if (matcher != true) {
            throw new WarningException("PR提交不规范, 内容: ${prTitle}")
        }
        logger.info "pr title 检查成功"
    }

    def checkPrCommits(List<String> prCommits) {
        prCommits.each {
            logger.debug("checkPrCommits: ${it}")
            def matcher = (it ==~ PR_COMMIT_CHECK_REX)
            if (matcher != true) {
                throw new WarningException("Commit提交不规范, 内容: ${it}")
            }
        }
        logger.info "pr commit 检查成功"
    }

    def checkoutPR(String sshAgentCredential, String baseBranch, String PrNumberStr = ""){
        List<String> prNumbers = PrNumberStr.split(" ")
        script.sshagent([sshAgentCredential]){
            script.sh "git checkout origin/${baseBranch}"
            for (pr in prNumbers){
                def PR_NAME="${script.env.BUILD_ID}_${pr}"
                script.sh "git fetch origin pull/${pr}/head:${PR_NAME}"
                logger.info "FETCH PR ${pr} DONE."
                script.sh "git merge ${PR_NAME}"
                logger.info "MERGE PR ${pr} DONE."
            }
        }
    }
}

package com.gladow.indexer4s.IndexResults

trait StageSucceeded { def msg: String }

case class StageSuccess(msg: String) extends StageSucceeded

case class RunResult(succeededStages: StageSucceeded*) {
  override def toString: String =
    s"""
       |Writing was successful!
       |Completed with:
       |${succeededStages.map(_.msg).mkString("\n")}
    """.stripMargin
}

case class IndexError(msg: String, succeededStages: List[StageSucceeded] = List.empty) {
  override def toString: String =
    s"""
       |Writing failed!
       |Completed stages:
       |${succeededStages.map(_.msg).mkString("\n")}
       |
       |But failed with: $msg
    """.stripMargin
}

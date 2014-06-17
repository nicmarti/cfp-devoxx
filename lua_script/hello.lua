local proposals = redis.call("KEYS", "Proposals:Votes:*")
redis.call("DEL", "Computed:Reviewer:Total")

for i = 1, #proposals do
  redis.log(redis.LOG_DEBUG, "==> " .. proposals[i])

  local uuidAndScores = redis.call("ZRANGEBYSCORE", proposals[i], 0, 11, "WITHSCORES")
  redis.call("HSET", "Computed:Scores", proposals[i], 0)
  redis.call("HSET", "Computed:Voters", proposals[i], 0)
  redis.call("HSET", "Computed:Average", proposals[i], 0)
  redis.call("HSET", "Computed:Median", proposals[i], 0)
  redis.call("HDEL", "Computed:Votes:ScoreAndCount", proposals[i])
  redis.call("HDEL", "Computed:VotersAbstention", proposals[i])
  redis.call("HDEL", "Computed:StandardDeviation" , proposals[i])

  for j=1,#uuidAndScores,2 do
    redis.log(redis.LOG_DEBUG, "uuid:" ..  uuidAndScores[j] .. " score:" .. uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Scores", proposals[i], uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Voters", proposals[i], 1)
    redis.call("HINCRBY", "Computed:Reviewer:Total", uuidAndScores[j], uuidAndScores[j + 1])
  end
end
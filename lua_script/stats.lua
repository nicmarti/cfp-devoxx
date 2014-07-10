local proposals = redis.call("KEYS", "Proposals:Votes:*")
redis.call("DEL", "Computed:Reviewer:Total")
redis.call("DEL", "Computed:Reviewer:ReviewedOne")

for i = 1, #proposals do
  redis.log(redis.LOG_DEBUG, "----------------- " .. proposals[i])

  redis.call("HSET", "Computed:Scores", proposals[i], 0)
  redis.call("HSET", "Computed:Voters", proposals[i], 0)
  redis.call("HSET", "Computed:Average", proposals[i], 0)
  redis.call("HSET", "Computed:Median", proposals[i], 0)
  redis.call("HDEL", "Computed:Votes:ScoreAndCount", proposals[i])
  redis.call("HDEL", "Computed:VotersAbstention", proposals[i])
  redis.call("HDEL", "Computed:StandardDeviation" , proposals[i])

  local uuidAndScores = redis.call("ZRANGEBYSCORE", proposals[i], 0, 11, "WITHSCORES")

  for j=1,#uuidAndScores,2 do
    redis.log(redis.LOG_DEBUG, "uuid:" ..  uuidAndScores[j] .. " score:" .. uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Scores", proposals[i], uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Voters", proposals[i], 1)
    redis.call("HINCRBY", "Computed:Reviewer:Total", uuidAndScores[j], uuidAndScores[j + 1])
    redis.call("SADD", "Computed:Reviewer:ReviewedOne",  uuidAndScores[j])
  end

redis.call("HDEL", "Computed:Median", proposals[i])

 local count = redis.call("HGET", "Computed:Voters", proposals[i])
 local total = redis.call("HGET", "Computed:Scores", proposals[i])
 local avg = 0
   if (count and total) then
        avg = tonumber(total)/tonumber(count)
        redis.call("HSET", "Computed:Average", proposals[i], avg)
   end

 redis.log(redis.LOG_DEBUG, "Average: " .. avg)

  local vm = 0
  local sum2 = 0
  local count2 = 0
  local standardDev

  for z=1,#uuidAndScores,2 do
      vm = uuidAndScores[z + 1] - avg
      sum2 = sum2 + (vm * vm)
      count2 = count2 + 1
  end

 redis.log(redis.LOG_DEBUG, "Standard Deviation sum2: " .. sum2)
 redis.log(redis.LOG_DEBUG, "Standard Deviation count2: " .. count2)
 if  sum2 < 1  then
  standardDev = 0
 else
  if(count2>1) then
     standardDev = math.sqrt(sum2 / (count2-1))
  else
    standardDev = 0
  end
 end

  redis.log(redis.LOG_DEBUG, "Standard Deviation: " .. standardDev)
  redis.call("HSET", "Computed:StandardDeviation" , proposals[i], standardDev)

 local countAbstention = redis.call("ZCOUNT", proposals[i], 0, 0)
 if(countAbstention>0) then
    redis.call("HSET", "Computed:VotersAbstention" , proposals[i], countAbstention)
 end
end
return #proposals
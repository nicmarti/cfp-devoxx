 -- Compute and store the total score for a proposal and
 -- the total number of voters
local proposals = redis.call("KEYS", "Proposals:Votes:*")
 redis.call("DEL", "Computed:Reviewer:Total")
for i = 1, #proposals do
  redis.log(redis.LOG_DEBUG, "----------------- " .. proposals[i])

 -- Do not select VOTE = 0
  local uuidAndScores = redis.call("ZRANGEBYSCORE", proposals[i], 1, 10, "WITHSCORES")
  redis.call("HSET", "Computed:Scores", proposals[i], 0)
  redis.call("HSET", "Computed:Voters", proposals[i], 0)
  redis.call("HSET", "Computed:Average", proposals[i], 0)
  redis.call("HSET", "Computed:Median", proposals[i], 0)
  redis.call("HDEL", "Computed:Votes:ScoreAndCount", proposals[i])
  redis.call("HDEL", "Computed:VotersAbstention", proposals[i])
  redis.call("HDEL", "Computed:StandardDeviation" , proposals[i])


 -- Compute total
  for j=1,#uuidAndScores,2 do
    -- redis.log(redis.LOG_DEBUG, "uuid:"..  uuidAndScores[j] .. " score:" .. uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Scores", proposals[i], uuidAndScores[j + 1])
    redis.call("HINCRBY", "Computed:Voters", proposals[i], 1)
    redis.call("HINCRBY", "Computed:Reviewer:Total", uuidAndScores[j], uuidAndScores[j + 1])
  end

 -- compute median

local withoutAbst = redis.call("ZRANGEBYSCORE", proposals[i], 1, 11)
local cnt = table.getn(withoutAbst)
redis.log(redis.LOG_DEBUG , "Voters " .. cnt)

if cnt > 0 then
        if cnt%2 > 0 then
                local mid = math.floor(cnt/2)
                -- redis.log(redis.LOG_DEBUG, "mid = " .. mid)
                local idMedian = withoutAbst[mid+1]
                -- redis.log(redis.LOG_DEBUG, "idMedian = " .. idMedian)
                local median1 = redis.call("ZSCORE",proposals[i],idMedian)
                -- redis.log(redis.LOG_DEBUG , "Mid1 via zscore " .. median1)
                redis.call("HSET", "Computed:Median", proposals[i], median1)
        else
                -- redis.log(redis.LOG_DEBUG, "CNT " .. cnt)
                local mid3 = math.floor(cnt/2)
                -- redis.log(redis.LOG_DEBUG, "Mid 2=" .. mid3)
                -- redis.log(redis.LOG_DEBUG, "ZRANGE " .. proposals[1]  .. " " .. mid3 .. " " .. mid3 +1 .. " WITHSCORES")
                local vals = redis.call("ZRANGE", proposals[1], mid3, mid3 + 1 , "WITHSCORES")
                local median2 = tostring((tonumber(vals[2]) + tonumber(vals[4]))/2.0)
                -- redis.log(redis.LOG_DEBUG, "median2 " .. median2)
                redis.call("HSET", "Computed:Median", proposals[i], median2)
        end
else
        redis.call("HDEL", "Computed:Median", proposals[i])
end


 -- compute average
 local count = redis.call("HGET", "Computed:Voters", proposals[i])
 local total = redis.call("HGET", "Computed:Scores", proposals[i])
 local avg = 0
   if (count and total) then
        avg = tonumber(total)/tonumber(count)
        redis.call("HSET", "Computed:Average", proposals[i], avg)
   end

 redis.log(redis.LOG_DEBUG, "Average: " .. avg)

 -- compute standard deviation
  local vm = 0
  local sum2 = 0
  local count2 = 0
  local standardDev

  for z=1,#uuidAndScores,2 do
      vm = uuidAndScores[z + 1] - avg
      sum2 = sum2 + (vm * vm)
      count2 = count2 + 1
  end

  standardDev = math.sqrt(sum2 / (count2-1))
  redis.log(redis.LOG_DEBUG, "Standard Deviation: " .. standardDev)
  redis.call("HSET", "Computed:StandardDeviation" , proposals[i], standardDev)

 -- compute abstention
 local countAbstention = redis.call("ZCOUNT", proposals[i], 0, 0)
 if(countAbstention>0) then
    redis.call("HSET", "Computed:VotersAbstention" , proposals[i], countAbstention)
 end
end
return #proposals
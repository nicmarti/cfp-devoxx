sudo docker ps -l

echo ""
echo "Are you sure you want to stop all these Docker containers ?"
read yn

yn=$(echo $yn | tr '[:upper:]' '[:lower:]')

if [ "${yn}" = "y" ]; 
	then
	echo "Stopping all docker containers."
	sudo docker stop $(sudo docker ps -l -q)
else
	echo "Stop operation was not executed."	
fi
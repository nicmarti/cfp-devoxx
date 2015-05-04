sudo docker ps -l

echo ""
echo "Are you sure you want to remove (forced) all these Docker containers ?"
read yn

yn=$(echo $yn | tr '[:upper:]' '[:lower:]')

if [ "${yn}" = "y" ]; 
	then
	echo "Removing (forced) all docker containers."
	# -f forces all containers to be removed
	sudo docker rm -f $(sudo docker ps -l -q)
else
	echo "Remove operation was not executed."
fi




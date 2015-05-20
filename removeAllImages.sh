sudo docker images

echo ""
echo "Are you sure you want to remove (forced) all these Docker images ?"
read yn

yn=$(echo $yn | tr '[:upper:]' '[:lower:]')

if [ "${yn}" = "y" ]; 
	then
	echo "Removing (forced) all docker images."
	# -f forces all images to be removed
	sudo docker rmi -f $(sudo docker images -q)
else
	echo "Remove operation was not executed."	
fi


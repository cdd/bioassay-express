service mongodb start
./baeconsole transfer importprovisional provisional_terms.ttl
./baeconsole transfer importassays assays_block*.zip
bin/catalina.sh start
echo "Ran Tomcat: http://localhost:8088/BioAssayExpress"
tail -f /dev/null

#!/bin/bash

echo -e "#!/bin/bash\n\nexec java -jar bin/main.jar \"conf/server.json\" \"\$@\"\n" > out/run.sh
chmod 770 out/*.sh


require('dotenv').config()
const SmeeClient = require('smee-client')

const smee = new SmeeClient({
	source: `https://smee.io/${process.env.SMEE_TOKEN}`,
	target: `${process.env.JENKINS_URL}/github-webhook/`,
	logger: console
	})

smee.start()

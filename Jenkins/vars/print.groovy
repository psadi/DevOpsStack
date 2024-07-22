#!groovy

def print(type, message){

    def NORMAL          = "\u001B[0m"
    def RED             = "\u001B[31m"
    def GREEN           = "\u001B[32m"
    def YELLOW          = "\u001B[33m"
    // def BLUE            = "\u001B[34m"

    switch(type.toUpperCase()) {
        case 'INFO':
            println GREEN + "[INFO] : " + message + NORMAL
        break

        case 'ERROR':
            println RED + "[ERROR] : " + message + NORMAL
        break

        case 'WARN':
            println YELLOW + "[WARN] : " + message + NORMAL
        break

        default:
            println "[ACE] : " +  message
    }

}
